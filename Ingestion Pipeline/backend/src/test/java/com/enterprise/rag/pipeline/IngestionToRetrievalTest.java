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

        List<String> matchedContents = retriever.retrieveParentContent("tenant-a", userQuery, 1);

        System.out.println("檢索命中內容：");
        for (String content : matchedContents) {
            System.out.println("👉 " + content);
        }

        // 斷言：應該要能精準命中包含 15 days 的那段完整 Parent Content
        assertThat(matchedContents).isNotEmpty();
        assertThat(matchedContents.get(0)).contains("15 days of annual paid leave");
    }
}
