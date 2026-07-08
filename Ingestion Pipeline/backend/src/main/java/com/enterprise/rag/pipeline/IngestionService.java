package com.enterprise.rag.pipeline;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmbeddingModel embeddingModel;

    /**
     * 工業級 Ingestion 入口：解析長文件並寫入父子表
     *
     * @param tenantId    租戶 ID
     * @param rawDocument 整篇原始大文件內容 (例如從 PDF/Markdown 讀進來的 String)
     * @param parentSize  父文區塊大小 (字數)
     * @param childSize   子文區塊大小 (字數)
     */
    @Transactional // 確保父子表寫入具備原子性，一出錯就全 Rollback
    public void ingestDocument(String tenantId, String rawDocument, int parentSize, int childSize) {


        // 1. 將整篇大文件切成多個 Parent Blocks (段落級別)
        List<String> parentChunks = splitText(rawDocument, parentSize);

        for (String parentContent : parentChunks) {
            String parentId = UUID.randomUUID().toString();

            // 2. 寫入 Parent 表 (不含向量，只存大文本與租戶標籤)
            jdbcTemplate.update(
                    "INSERT INTO parent_documents (id, tenant_id, content) VALUES (?, ?, ?)",
                    parentId, tenantId, parentContent
            );

            // 3. 將這個 Parent 內容，進一步細切成多個 Child Chunks (句子級別)
            List<String> childChunks = splitText(parentContent, childSize);

            List<Object[]> batchArgs = new ArrayList<>();

            for (String childContent : childChunks) {
                String childId = UUID.randomUUID().toString();

                // 4. 呼叫 Gemini 產生子區塊的向量 (768 維)
                Embedding embedding = embeddingModel.embed(childContent).content();
                float[] vector = embedding.vector();

                // 準備 Batch 寫入的參數
                batchArgs.add(new Object[]{childId, parentId, tenantId, childContent, vector});
            }

            // 5. 使用 JDBC Batch 批量寫入子表，大幅壓低資料庫 I/O 耗時
            jdbcTemplate.batchUpdate(
                    "INSERT INTO child_vectors (id, parent_id, tenant_id, content, embedding) VALUES (?, ?, ?, ?, ?::vector)",
                    batchArgs
            );
        }


    }

    /**
     * 簡易文字切片演算法 (示範用，實務上可換成 LangChain4j 的 DocumentSplitters)
     */
    private List<String> splitText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        int length = text.length();
        for (int i = 0; i < length; i += chunkSize) {
            chunks.add(text.substring(i, Math.min(length, i + chunkSize)));
        }
        return chunks;
    }
}
