package com.enterprise.rag.pipeline;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Service
public class TenantRerankRetriever {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EmbeddingModel embeddingModel;

    /**
     * 核心檢索方法：結合向量檢索與全文檢索 (Hybrid Search)，並嚴格限制租戶
     */
    public List<String> retrieveParentContent(String tenantId, String query, int maxResults, double distanceThreshold) {
        // 1. 將用戶 Query 轉為向量
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        float[] vectorArray = queryEmbedding.vector();

        // 2. 執行 SQL 級別的混合檢索 (Hybrid Search)
        // 使用 RRF (Reciprocal Rank Fusion) 演算法思想，在 SQL 內將全文檢索評分與向量距離混合排序
        String hybridSql = """
                    WITH vector_search AS (
                        SELECT parent_id, 
                               ROW_NUMBER() OVER (ORDER BY embedding <=> ?::vector) as rank
                        FROM child_vectors
                        WHERE tenant_id = ?
                        AND (embedding <=> ?::vector) < ?  -- 餘弦距離大於門檻的直接拋棄，不參與 RRF
                        LIMIT 20
                    ),
                    keyword_search AS (
                        SELECT parent_id,
                               ROW_NUMBER() OVER (ORDER BY ts_rank_cd(to_tsvector('english', content), plainto_tsquery('english', ?)) DESC) as rank
                        FROM child_vectors
                        WHERE tenant_id = ? AND to_tsvector('english', content) @@ plainto_tsquery('english', ?)
                        LIMIT 20
                    )
                    SELECT p.content,
                           COALESCE(1.0 / (60 + v.rank), 0.0) + COALESCE(1.0 / (60 + k.rank), 0.0) as rrf_score
                    FROM parent_documents p
                    LEFT JOIN vector_search v ON p.id = v.parent_id
                    LEFT JOIN keyword_search k ON p.id = k.parent_id
                    WHERE p.tenant_id = ? AND (v.parent_id IS NOT NULL OR k.parent_id IS NOT NULL)
                    ORDER BY rrf_score DESC
                    LIMIT ?;
                """;

        List<String> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(hybridSql)) {

            // 綁定參數 (防範 SQL 注入，同時確保多租戶安全隔離)
            stmt.setObject(1, vectorArray);
            stmt.setString(2, tenantId);
            stmt.setObject(3, vectorArray);
            stmt.setDouble(4, distanceThreshold);
            stmt.setString(5, query);
            stmt.setString(6, tenantId);
            stmt.setString(7, query);

            stmt.setString(8, tenantId);        // 最外層 p.tenant_id
            stmt.setInt(9, maxResults);         // LIMIT ?

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(rs.getString("content"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute high-performance hybrid search", e);
        }

        return results;
    }
}