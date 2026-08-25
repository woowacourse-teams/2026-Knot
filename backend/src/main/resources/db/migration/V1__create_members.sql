CREATE TABLE members (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    github_id BIGINT NOT NULL,
    nickname VARCHAR(20) NOT NULL,
    profile_image_url VARCHAR(500),
    CONSTRAINT pk_member PRIMARY KEY (id),
    CONSTRAINT uk_member_github_id UNIQUE (github_id),
    CONSTRAINT ck_member_github_id_positive CHECK (github_id > 0),
    CONSTRAINT ck_member_nickname_not_blank CHECK (btrim(nickname) <> ''),
    CONSTRAINT ck_member_profile_image_not_blank
        CHECK (profile_image_url IS NULL OR btrim(profile_image_url) <> '')
);
