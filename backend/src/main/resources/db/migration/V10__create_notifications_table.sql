
CREATE TABLE notifications (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                               user_id UUID NOT NULL,
                               document_id UUID,

                               title VARCHAR(150) NOT NULL,
                               message TEXT NOT NULL,
                               type VARCHAR(30) NOT NULL,

                               is_read BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_notifications_user
                                   FOREIGN KEY (user_id)
                                       REFERENCES users(id)
                                       ON DELETE CASCADE,

                               CONSTRAINT fk_notifications_document
                                   FOREIGN KEY (document_id)
                                       REFERENCES documents(id)
                                       ON DELETE SET NULL
);

CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE INDEX idx_notifications_user_unread
    ON notifications (user_id, is_read)
    WHERE is_read = FALSE;
