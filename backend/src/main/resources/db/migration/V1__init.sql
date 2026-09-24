-- HistoryGenie — Migration V1
-- Flyway esegue questo file automaticamente al primo avvio di Spring Boot.
-- Percorso: src/main/resources/db/migration/V1__init.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Users ─────────────────────────────────────────────────────────────────────

CREATE TABLE users (
                       id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                       username      VARCHAR(64)  NOT NULL,
                       email         VARCHAR(255) NOT NULL,
                       password_hash TEXT         NOT NULL,
                       enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       CONSTRAINT uk_users_username UNIQUE (username),
                       CONSTRAINT uk_users_email    UNIQUE (email)
);

CREATE TABLE user_roles (
                            user_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            role    VARCHAR(32) NOT NULL,
                            PRIMARY KEY (user_id, role),
                            CONSTRAINT chk_role CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'))
);

-- ── Collections ───────────────────────────────────────────────────────────────

CREATE TABLE collections (
                             id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                             name               VARCHAR(255) NOT NULL,
                             description        TEXT,
                             visibility         VARCHAR(32)  NOT NULL DEFAULT 'PRIVATE',
                             ragflow_dataset_id VARCHAR(255),
                             owner_id           UUID         NOT NULL REFERENCES users(id),
                             created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                             CONSTRAINT uk_ragflow_dataset UNIQUE (ragflow_dataset_id),
                             CONSTRAINT chk_visibility     CHECK (visibility IN ('PRIVATE', 'PUBLIC'))
);

CREATE INDEX idx_collection_owner ON collections(owner_id);

CREATE TABLE collection_members (
                                    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                    collection_id UUID        NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
                                    user_id       UUID        NOT NULL REFERENCES users(id)       ON DELETE CASCADE,
                                    role          VARCHAR(32) NOT NULL DEFAULT 'VIEWER',
                                    CONSTRAINT uk_collection_member UNIQUE (collection_id, user_id),
                                    CONSTRAINT chk_member_role      CHECK (role IN ('VIEWER', 'EDITOR', 'ADMIN'))
);

CREATE INDEX idx_member_collection ON collection_members(collection_id);
CREATE INDEX idx_member_user       ON collection_members(user_id);

-- ── Documents ─────────────────────────────────────────────────────────────────

CREATE TABLE documents (
                           id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                           original_name    VARCHAR(512) NOT NULL,
                           stored_filename  VARCHAR(512) NOT NULL,
                           file_type        VARCHAR(64)  NOT NULL,
                           file_size_bytes  BIGINT       NOT NULL,
                           uploaded_by      UUID         NOT NULL REFERENCES users(id),
                           historical_date  DATE,
                           author           VARCHAR(255),
                           archive_source   VARCHAR(512),
                           created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_document_uploader ON documents(uploaded_by);
CREATE INDEX idx_document_filetype ON documents(file_type);
CREATE INDEX idx_document_created  ON documents(created_at);

-- ── Collection ↔ Document (N:M) ───────────────────────────────────────────────

CREATE TABLE collection_documents (
                                      id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                      collection_id  UUID        NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
                                      document_id    UUID        NOT NULL REFERENCES documents(id)   ON DELETE CASCADE,
                                      ragflow_doc_id VARCHAR(255),
                                      status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                                      added_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                      CONSTRAINT uk_collection_document UNIQUE (collection_id, document_id),
                                      CONSTRAINT chk_coldoc_status
                                          CHECK (status IN ('PENDING', 'PROCESSING', 'PARSED', 'ERROR'))
);

CREATE INDEX idx_coldoc_collection ON collection_documents(collection_id);
CREATE INDEX idx_coldoc_document   ON collection_documents(document_id);
CREATE INDEX idx_coldoc_status     ON collection_documents(status);

-- Indice parziale: ottimizza la query "documenti ancora da elaborare"
CREATE INDEX idx_coldoc_pending
    ON collection_documents(collection_id)
    WHERE status = 'PENDING';

-- ── Chat Sessions ─────────────────────────────────────────────────────────────

CREATE TABLE chat_sessions (
                               id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                               title                   VARCHAR(512),
                               ragflow_conversation_id VARCHAR(255),
                               user_id                 UUID         NOT NULL REFERENCES users(id),
                               collection_id           UUID         NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
                               created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               CONSTRAINT uk_ragflow_conversation UNIQUE (ragflow_conversation_id)
);

CREATE INDEX idx_chatsession_user       ON chat_sessions(user_id);
CREATE INDEX idx_chatsession_collection ON chat_sessions(collection_id);