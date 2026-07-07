package com.enterprise.rag.pipeline;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;


@SpringBootTest(classes = com.enterprise.RagApplication.class)
public class TenantRerankRetrieverTest {
    @Autowired
    private TenantRerankRetriever retriever;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${google.gemini.api-key}")
    private String geminiApiKey;

    @Value("${google.gemini.ai-model}")
    private String aiModel;

    @Value("${google.gemini.ai-dim}")
    private int aiDim;

    @Test
    public void testHybridSearchWithGoogleGemini() {
        // ---- 1. 清除舊資料並準備測試假數據 ----
        jdbcTemplate.execute("TRUNCATE TABLE child_vectors CASCADE;");
        jdbcTemplate.execute("TRUNCATE TABLE parent_documents CASCADE;");

        // 插入一筆 HR 相關的知識庫（屬於租戶: tenant-a）
        jdbcTemplate.execute("INSERT INTO parent_documents(id, tenant_id, content) VALUES (1, 'tenant-a', 'The company provides 15 days of annual paid leave for all full-time software engineers.');");


        // 為了方便測試，這裡直接抓取環境變數，或是你也可以手動填入你的 Gemini Key String
        EmbeddingModel model = GoogleAiEmbeddingModel.builder()
                .apiKey(geminiApiKey)
                .modelName(aiModel)
                .outputDimensionality(aiDim)
                .build();

        String childText = "15 days of annual paid leave for software engineers";
        float[] fakeVector = model.embed(childText).content().vector();

        String childId = UUID.randomUUID().toString();

        // 插入子向量表
        jdbcTemplate.update(
                "INSERT INTO child_vectors(id, parent_id, tenant_id, content, embedding) " +
                        "VALUES (?, '1', 'tenant-a', ?, ?::vector) " +
                        "ON CONFLICT (id) DO NOTHING",
                childId, childText, fakeVector
        );

        // ---- 2. 測試混合檢索 ----
        System.out.println("====== [開始測試 Google 向量混合檢索] ======");

        // 模擬用戶發問
        String userQuery = "How many days of paid vacation do developers get?";

        // 呼叫你的核心 RRF 檢索方法（尋找租戶 tenant-a 的資料）
//        List<String> results = retriever.retrieveParentContent("tenant-a", userQuery, 3);
//
//        System.out.println("檢索到的結果數量: " + results.size());
//        for (String doc : results) {
//            System.out.println("👉 找到命中知識庫內容: " + doc);
//        }
//
//        // ---- 3. 驗證多租戶隔離（用 tenant-b 去撈，應該要什麼都撈不到） ----
//        System.out.println("====== [測試多租戶安全隔離] ======");
//        List<String> emptyResults = retriever.retrieveParentContent("tenant-b", userQuery, 3);
//        System.out.println("租戶 tenant-b 檢索到的結果數量 (預期為 0): " + emptyResults.size());
    }
}
