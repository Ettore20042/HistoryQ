ALTER TABLE documents
    ADD COLUMN storage_path VARCHAR(1024);

CREATE INDEX idx_document_storage_path
    ON documents(storage_path);