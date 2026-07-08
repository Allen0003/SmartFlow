package com.enterprise.rag.pipeline;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Service
public class IngestionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmbeddingModel embeddingModel;

    /**
     * 工業級 Ingestion 入口：解析長文件並寫入父子表（使用 LangChain4j DocumentSplitters）
     *
     * @param tenantId    租戶 ID
     * @param rawDocument 整篇原始大文件內容
     * @param parentSize  父文區塊最大字數 (e.g., 300)
     * @param childSize   子文區塊最大字數 (e.g., 100)
     */
    @Transactional // 確保父子表寫入具備原子性
    public void ingestDocument(String tenantId, String rawDocument, int parentSize, int childSize) {
        if (rawDocument == null || rawDocument.isBlank()) {
            return;
        }


        // 1. 初始化 LangChain4j 的 Splitters
        // 建議加上重疊區（Overlap），父區塊設個 30 字，子區塊設個 10-20 字，防範語意在邊界斷掉
        DocumentSplitter parentSplitter = DocumentSplitters.recursive(parentSize, 30);
        DocumentSplitter childSplitter = DocumentSplitters.recursive(childSize, 10);

        // 2. 將整篇大文件轉為 LangChain4j Document，並切成多個 Parent Segments
        Document mainDocument = Document.from(rawDocument);
        List<TextSegment> parentSegments = parentSplitter.split(mainDocument);

        for (TextSegment parentSegment : parentSegments) {
            String parentContent = parentSegment.text();
            String parentId = UUID.randomUUID().toString();

            // 3. 寫入 Parent 表
            jdbcTemplate.update(
                    "INSERT INTO parent_documents (id, tenant_id, content) VALUES (?, ?, ?)",
                    parentId, tenantId, parentContent
            );

            // 4. 將這個 Parent 內容包成臨時 Document，進一步細切成多個 Child Segments
            Document parentDocMock = Document.from(parentContent);
            List<TextSegment> childSegments = childSplitter.split(parentDocMock);

            List<Object[]> batchArgs = new ArrayList<>();

            for (TextSegment childSegment : childSegments) {
                String childContent = childSegment.text();
                String childId = UUID.randomUUID().toString();

                // 5. 呼叫 Gemini 產生子區塊的向量
                Embedding embedding = embeddingModel.embed(childContent).content();
                float[] vector = embedding.vector();

                // 準備 Batch 寫入的參數
                batchArgs.add(new Object[]{childId, parentId, tenantId, childContent, vector});
            }

            // 6. 使用 JDBC Batch 批量寫入子表
            jdbcTemplate.batchUpdate(
                    "INSERT INTO child_vectors (id, parent_id, tenant_id, content, embedding) VALUES (?, ?, ?, ?, ?::vector)",
                    batchArgs
            );
        }
    }
}
