-- 建立向量擴充功能
CREATE EXTENSION IF NOT EXISTS vector;

-- Parent 表：存放完整上下文
CREATE TABLE parent_documents (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_parent_tenant ON parent_documents(tenant_id);

-- Child 表：存放向量，用於高併發檢索
CREATE TABLE child_vectors (
    id VARCHAR(36) PRIMARY KEY,
    parent_id VARCHAR(36) NOT NULL REFERENCES parent_documents(id) ON DELETE CASCADE,
    tenant_id VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    embedding VECTOR(1536), -- 假設使用 OpenAI text-embedding-3-small (1536維)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- 建立租戶與向量複合索引 (HNSW)
CREATE INDEX idx_child_tenant_vector ON child_vectors USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);