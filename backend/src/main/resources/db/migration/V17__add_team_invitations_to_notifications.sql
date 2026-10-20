ALTER TABLE notifications
ADD COLUMN team_invitation_id UUID;

ALTER TABLE notifications
ADD CONSTRAINT fk_notifications_team_invitation
FOREIGN KEY (team_invitation_id)
REFERENCES team_invitations(id);