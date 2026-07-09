package com.enterprise.rag.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.enterprise.RagApplication.class)
public class IngestionToRetrievalTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private TenantRerankRetriever retriever;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testEndToEndPipeline() {
        // 清空環境
        jdbcTemplate.execute("TRUNCATE TABLE child_vectors CASCADE;");
        jdbcTemplate.execute("TRUNCATE TABLE parent_documents CASCADE;");

        // 1. 模擬一份未經處理的長篇租戶內部文件 ( tenant-a )
        String companyPolicyDoc =
                "Welcome to Enterprise Corp. This document contains sensitive operational guidelines. " +
                        "Regarding engineering employee benefits: All full-time software engineers are entitled to 15 days of annual paid leave, " +
                        "which accrues monthly starting from the first day of employment. " +
                        "Regarding hardware equipment: Engineers will receive a high-end workstation or laptop upon onboarding, " +
                        "which must be returned to the IT department when offboarding.";

        // 2. 執行 Ingestion (設定 Parent 切 150 字，Child 緊密切 50 字)
        ingestionService.ingestDocument("tenant-a", companyPolicyDoc, 150, 50);

        // 驗證資料庫真的有東西了
        Integer parentCount = jdbcTemplate.queryForObject("SELECT count(*) FROM parent_documents", Integer.class);
        Integer childCount = jdbcTemplate.queryForObject("SELECT count(*) FROM child_vectors", Integer.class);
        System.out.println("====== [Ingestion 成果] ======");
        System.out.println("生成 Parent 數量: " + parentCount);
        System.out.println("生成 Child 向量數量: " + childCount);

        assertThat(parentCount).isGreaterThan(0);
        assertThat(childCount).isGreaterThan(0);

        // 3. 馬上測試下游 Retrieval 檢索
        System.out.println("====== [開始 End-to-End 檢索測試] ======");
        String userQuery = "What vacation benefits do developers have?";

        List<String> matchedContents = retriever.retrieveParentContent("tenant-a", userQuery, 1, 0.5);

        System.out.println("檢索命中內容：");
        for (String content : matchedContents) {
            System.out.println("👉 " + content);
        }

        // 斷言：應該要能精準命中包含 15 days 的那段完整 Parent Content
        assertThat(matchedContents).isNotEmpty();
        assertThat(matchedContents.get(0)).contains("15 days of annual paid leave");
    }


    @Test
    public void testThresholdFilteringWithNonsenseQuery() {
        // 1. 確保資料庫有剛才塞進去的軟體工程師福利資料 (這筆資料在 tenant-a 下)
        // 假設上一筆測試的 Ingestion 已經跑過，資料庫已有資料

        System.out.println("====== [開始測試：動態語意相似度門檻過濾] ======");

        // 2. 模擬一個跟 HR 知識庫完全不相干的 query
        String nonsenseQuery = "What is the best recipe for making Italian pepperoni pizza?";

        // 3. 呼叫進階檢索方法，傳入嚴格的餘弦距離門檻 0.4 (pgvector 距離越小越相似)
        // 如果距離大於 0.4 代表不相似，應該會被 SQL 直接過濾掉
        List<String> results = retriever.retrieveParentContent("tenant-a", nonsenseQuery,
                3, 0.4);

        System.out.println("不相干問題檢索到的結果數量 (預期為 0): " + results.size());

        // 4. 斷言驗證：因為是披薩食譜，知識庫完全不該命中任何 HR 福利，結果必須為空！
        assertThat(results).isEmpty();
    }

}
