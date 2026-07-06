-- 建立向量擴充功能
CREATE EXTENSION IF NOT EXISTS vector;

-- Parent 表：存放完整上下文
CREATE TABLE IF NOT EXISTS parent_documents (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 建立索引（PostgreSQL 9.5+ 支援 CREATE INDEX IF NOT EXISTS）
CREATE INDEX IF NOT EXISTS idx_parent_tenant ON parent_documents(tenant_id);

-- Child 表：存放向量
CREATE TABLE IF NOT EXISTS child_vectors (
    id VARCHAR(36) PRIMARY KEY,
    parent_id VARCHAR(36) NOT NULL REFERENCES parent_documents(id) ON DELETE CASCADE,
    tenant_id VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    embedding VECTOR(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 建立租戶與向量複合索引 (HNSW)
CREATE INDEX IF NOT EXISTS idx_child_tenant_vector ON child_vectors USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);