CREATE TABLE document_pages (
                                id UUID PRIMARY KEY,
                                document_id UUID NOT NULL,
                                filename VARCHAR(255) NOT NULL,
                                ocr_text TEXT,

                                CONSTRAINT fk_document_pages_document
                                    FOREIGN KEY (document_id)
                                        REFERENCES documents(id)
                                        ON DELETE CASCADE,

                                CONSTRAINT uk_document_pages_document_filename
                                    UNIQUE (document_id, filename)
);