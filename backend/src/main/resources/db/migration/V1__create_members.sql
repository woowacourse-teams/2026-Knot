CREATE TABLE members (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    nickname VARCHAR(20) NOT NULL,
    profile_image_url VARCHAR(500),

    CONSTRAINT pk_member
     PRIMARY KEY (id),

    CONSTRAINT ck_member_nickname_not_blank
     CHECK (btrim(nickname) <> ''),

    CONSTRAINT ck_member_profile_image_not_blank
     CHECK (
         profile_image_url IS NULL
             OR btrim(profile_image_url) <> ''
         )
);

CREATE TABLE oauth_identities (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    member_id BIGINT NOT NULL,

    CONSTRAINT pk_oauth_identity
      PRIMARY KEY (id),

    CONSTRAINT uk_oauth_identity_provider_user
      UNIQUE (provider, provider_user_id),

    CONSTRAINT uk_oauth_identity_member_provider
      UNIQUE (member_id, provider),

    CONSTRAINT fk_oauth_identity_member
      FOREIGN KEY (member_id)
          REFERENCES members (id),

    CONSTRAINT ck_oauth_identity_provider_not_blank
      CHECK (btrim(provider) <> ''),

    CONSTRAINT ck_oauth_identity_provider_user_not_blank
      CHECK (btrim(provider_user_id) <> '')
);
