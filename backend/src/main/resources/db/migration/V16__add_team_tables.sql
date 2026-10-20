CREATE TABLE teams (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
name VARCHAR(100) NOT NULL,
description TEXT,
created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

CONSTRAINT chk_teams_name_not_blank
    CHECK (length(trim(name)) > 0)

);

CREATE INDEX idx_teams_created_by
ON teams(created_by);

CREATE TABLE team_members (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
role VARCHAR(10) NOT NULL DEFAULT 'MEMBER',


CONSTRAINT uq_team_members_team_user
    UNIQUE (team_id, user_id),

CONSTRAINT chk_team_members_role
    CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))

);

CREATE INDEX idx_team_members_user_id
ON team_members(user_id);

CREATE INDEX idx_team_members_team_role
ON team_members(team_id, role);

CREATE TABLE team_documents (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
document_id UUID NOT NULL REFERENCES documents(id) ON DELETE RESTRICT,
added_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,


CONSTRAINT uq_team_documents_team_document
    UNIQUE (team_id, document_id)

);

CREATE INDEX idx_team_documents_document_id
ON team_documents(document_id);

CREATE TABLE team_collections (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE RESTRICT,
added_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,


CONSTRAINT uq_team_collections_team_collection
    UNIQUE (team_id, collection_id)

);

CREATE INDEX idx_team_collections_collection_id
ON team_collections(collection_id);

CREATE TABLE team_invitations (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
email VARCHAR(320) NOT NULL,
invited_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
token_hash VARCHAR(255) NOT NULL UNIQUE,
status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
expires_at TIMESTAMPTZ NOT NULL,


CONSTRAINT chk_team_invitations_status
    CHECK (status IN (
        'PENDING',
        'ACCEPTED',
        'DECLINED'
    )),

CONSTRAINT chk_team_invitations_expiration
    CHECK (expires_at > created_at)

);

CREATE INDEX idx_team_invitations_team_status
ON team_invitations(team_id, status);

CREATE INDEX idx_team_invitations_email
ON team_invitations(lower(email));