ALTER TABLE documents
    ADD COLUMN display_name VARCHAR(512);

UPDATE documents
SET display_name = original_name;

ALTER TABLE documents
    ALTER COLUMN display_name SET NOT NULL;

ALTER TABLE documents
    ADD COLUMN description TEXT NOT NULL DEFAULT '';