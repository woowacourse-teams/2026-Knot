ALTER TABLE workspace_invitations
    ADD COLUMN link_token_ciphertext VARCHAR(512),
    ADD COLUMN invite_code_ciphertext VARCHAR(512),
    ADD CONSTRAINT chk_workspace_invitations_secret_envelopes
        CHECK (
            (link_token_ciphertext IS NULL AND invite_code_ciphertext IS NULL)
            OR
            (link_token_ciphertext IS NOT NULL AND invite_code_ciphertext IS NOT NULL)
        );
