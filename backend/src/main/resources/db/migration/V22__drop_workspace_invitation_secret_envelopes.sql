ALTER TABLE workspace_invitations
    DROP CONSTRAINT chk_workspace_invitations_secret_envelopes,
    DROP COLUMN link_token_ciphertext,
    DROP COLUMN invite_code_ciphertext;
