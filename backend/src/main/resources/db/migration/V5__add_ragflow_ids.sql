ALTER TABLE documents
    ADD COLUMN ragflow_dataset_id VARCHAR(128);

ALTER TABLE documents
    ADD COLUMN ragflow_chat_id VARCHAR(128);