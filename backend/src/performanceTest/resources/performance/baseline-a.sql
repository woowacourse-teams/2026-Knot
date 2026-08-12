-- =========================================================
-- Knot Notion Import ERD v2.1
-- PostgreSQL 15+
-- ERDCloud import용
-- 상태: A안 TARGET DDL (2026-08-10, ADR-001 / D-014)
-- 구현 전 보완 항목은 docs/02-architecture/notion-import-architecture.md의
-- "v2.1 DDL 보완 목록"과 docs/05-roadmap/notion-import-roadmap.md를 따른다.
--
-- 설계 원칙
-- 1. Knot 사용자/워크스페이스와 Notion 사용자/연결을 분리한다.
-- 2. Notion 원본은 snapshots에 보존한다.
-- 3. objects가 Page/Database/Data Source/Block 계층의 Source of Truth다.
-- 4. page_links, page_render_snapshots는 재생성 가능한 Projection이다.
-- 5. Property의 단일 값은 page_property_values에 통합하고,
--    다중 값만 연결 테이블로 분리한다.
-- =========================================================


-- =========================================================
-- 1. Knot 서비스 사용자 / 워크스페이스
-- =========================================================

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    name VARCHAR(255) NOT NULL,
    profile_image_url VARCHAR(2048),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE workspaces (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_workspaces_created_by
        FOREIGN KEY (created_by_user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT
);

CREATE TABLE workspace_members (
    workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(30) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_workspace_members
        PRIMARY KEY (workspace_id, user_id),

    CONSTRAINT fk_workspace_members_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_workspace_members_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);


-- =========================================================
-- 2. Notion OAuth / Connection
-- =========================================================

CREATE TABLE notion_oauth_states (
    state VARCHAR(255) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    workspace_id BIGINT,
    redirect_path VARCHAR(1000),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notion_oauth_states_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_notion_oauth_states_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id)
        ON DELETE CASCADE
);

CREATE TABLE notion_connections (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    connected_by_user_id BIGINT NOT NULL,

    bot_id VARCHAR(100) NOT NULL,
    notion_workspace_id VARCHAR(100) NOT NULL,
    notion_workspace_name VARCHAR(255),
    notion_workspace_icon VARCHAR(2048),
    notion_owner_user_id VARCHAR(100),

    encrypted_access_token TEXT NOT NULL,
    encrypted_refresh_token TEXT,
    access_token_expires_at TIMESTAMPTZ,

    connection_status VARCHAR(30) NOT NULL DEFAULT 'CONNECTED',
    connected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_refreshed_at TIMESTAMPTZ,
    disconnected_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_notion_connections_bot_id
        UNIQUE (bot_id),

    CONSTRAINT fk_notion_connections_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_notion_connections_connected_by
        FOREIGN KEY (connected_by_user_id)
        REFERENCES users (id)
        ON DELETE RESTRICT
);

CREATE TABLE notion_users (
    id BIGSERIAL PRIMARY KEY,
    notion_connection_id BIGINT NOT NULL,
    notion_user_id VARCHAR(100) NOT NULL,
    user_type VARCHAR(30) NOT NULL,
    name VARCHAR(255),
    email VARCHAR(320),
    avatar_url VARCHAR(2048),
    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_notion_users_connection_user
        UNIQUE (notion_connection_id, notion_user_id),

    CONSTRAINT fk_notion_users_connection
        FOREIGN KEY (notion_connection_id)
        REFERENCES notion_connections (id)
        ON DELETE CASCADE
);


-- =========================================================
-- 3. Rich Text 공통 모델
-- =========================================================

CREATE TABLE rich_text_contents (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- =========================================================
-- 4. Notion 공통 Object / 하위 타입
-- =========================================================

CREATE TABLE objects (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    notion_connection_id BIGINT NOT NULL,

    -- Knot DB에서 연결된 실제 부모 Object.
    -- 부모가 아직 저장되지 않았거나 workspace/agent라면 NULL일 수 있다.
    parent_object_id BIGINT,

    -- Notion API parent 원본 정보.
    -- 지원 타입: page_id, block_id, database_id, data_source_id, workspace, agent_id
    external_parent_type VARCHAR(30),
    external_parent_notion_id VARCHAR(100),

    -- data_source_id parent 응답에 함께 포함되는 database_id 편의 값.
    -- 계층의 Source of Truth는 parent_object_id이며, 이 컬럼은 원본 보존/조회 편의용이다.
    parent_database_notion_id VARCHAR(100),

    -- Notion API가 반환한 parent 객체 전체를 보존한다.
    parent_payload JSONB,

    notion_object_id VARCHAR(100),
    object_type VARCHAR(30) NOT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'NOTION',
    position_index INTEGER,

    created_by_notion_user_id BIGINT,
    updated_by_notion_user_id BIGINT,

    notion_created_at TIMESTAMPTZ,
    notion_updated_at TIMESTAMPTZ,
    is_in_trash BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_objects_connection_notion
        UNIQUE (notion_connection_id, notion_object_id),

    CONSTRAINT ck_objects_external_parent_type
        CHECK (
            external_parent_type IS NULL
            OR external_parent_type IN (
                'page_id',
                'block_id',
                'database_id',
                'data_source_id',
                'workspace',
                'agent_id'
            )
        ),

    CONSTRAINT fk_objects_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_objects_connection
        FOREIGN KEY (notion_connection_id)
        REFERENCES notion_connections (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_objects_parent
        FOREIGN KEY (parent_object_id)
        REFERENCES objects (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_objects_created_by
        FOREIGN KEY (created_by_notion_user_id)
        REFERENCES notion_users (id)
        ON DELETE SET NULL,

    CONSTRAINT fk_objects_updated_by
        FOREIGN KEY (updated_by_notion_user_id)
        REFERENCES notion_users (id)
        ON DELETE SET NULL
);

CREATE TABLE pages (
    object_id BIGINT PRIMARY KEY,
    title_rich_text_id BIGINT,
    plain_title VARCHAR(1000),

    page_kind VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    url VARCHAR(2048),
    public_url VARCHAR(2048),

    icon_payload JSONB,
    cover_payload JSONB,

    is_locked BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_pages_object
        FOREIGN KEY (object_id)
        REFERENCES objects (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_pages_title
        FOREIGN KEY (title_rich_text_id)
        REFERENCES rich_text_contents (id)
        ON DELETE SET NULL
);

CREATE TABLE databases (
    object_id BIGINT PRIMARY KEY,
    title_rich_text_id BIGINT,
    description_rich_text_id BIGINT,

    plain_title VARCHAR(1000),
    plain_description TEXT,
    is_inline BOOLEAN NOT NULL DEFAULT FALSE,

    icon_payload JSONB,
    cover_payload JSONB,

    CONSTRAINT fk_databases_object
        FOREIGN KEY (object_id)
        REFERENCES objects (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_databases_title
        FOREIGN KEY (title_rich_text_id)
        REFERENCES rich_text_contents (id)
        ON DELETE SET NULL,

    CONSTRAINT fk_databases_description
        FOREIGN KEY (description_rich_text_id)
        REFERENCES rich_text_contents (id)
        ON DELETE SET NULL
);

CREATE TABLE data_sources (
    object_id BIGINT PRIMARY KEY,
    title_rich_text_id BIGINT,
    description_rich_text_id BIGINT,

    plain_title VARCHAR(1000),
    plain_description TEXT,

    CONSTRAINT fk_data_sources_object
        FOREIGN KEY (object_id)
        REFERENCES objects (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_data_sources_title
        FOREIGN KEY (title_rich_text_id)
        REFERENCES rich_text_contents (id)
        ON DELETE SET NULL,

    CONSTRAINT fk_data_sources_description
        FOREIGN KEY (description_rich_text_id)
        REFERENCES rich_text_contents (id)
        ON DELETE SET NULL
);

CREATE TABLE blocks (
    object_id BIGINT PRIMARY KEY,
    synced_from_block_object_id BIGINT,

    block_type VARCHAR(50) NOT NULL,
    has_children BOOLEAN NOT NULL DEFAULT FALSE,

    -- Rich Text 자체가 아니라 타입별 설정과 fallback 원본
    block_payload JSONB,

    CONSTRAINT fk_blocks_object
        FOREIGN KEY (object_id)
        REFERENCES objects (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_blocks_synced_from
        FOREIGN KEY (synced_from_block_object_id)
        REFERENCES blocks (object_id)
        ON DELETE SET NULL
);


-- =========================================================
-- 5. Rich Text Segment / Mention / Block 연결
-- =========================================================

CREATE TABLE rich_text_segments (
    id BIGSERIAL PRIMARY KEY,
    rich_text_content_id BIGINT NOT NULL,
    position_index INTEGER NOT NULL,

    segment_type VARCHAR(30) NOT NULL,
    plain_text TEXT,
    href VARCHAR(2048),

    text_content TEXT,
    link_url VARCHAR(2048),
    equation_expression TEXT,

    annotations JSONB,
    segment_payload JSONB,

    CONSTRAINT uk_rich_text_segments_position
        UNIQUE (rich_text_content_id, position_index),

    CONSTRAINT fk_rich_text_segments_content
        FOREIGN KEY (rich_text_content_id)
        REFERENCES rich_text_contents (id)
        ON DELETE CASCADE
);

CREATE TABLE rich_text_mentions (
    rich_text_segment_id BIGINT PRIMARY KEY,
    mention_type VARCHAR(30) NOT NULL,

    target_notion_user_id BIGINT,
    target_page_object_id BIGINT,
    target_database_object_id BIGINT,
    target_data_source_object_id BIGINT,

    mention_date_start TIMESTAMPTZ,
    mention_date_end TIMESTAMPTZ,
    mention_timezone VARCHAR(100),
    link_preview_url VARCHAR(2048),

    mention_payload JSONB,

    CONSTRAINT fk_rich_text_mentions_segment
        FOREIGN KEY (rich_text_segment_id)
        REFERENCES rich_text_segments (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rich_text_mentions_user
        FOREIGN KEY (target_notion_user_id)
        REFERENCES notion_users (id)
        ON DELETE SET NULL,

    CONSTRAINT fk_rich_text_mentions_page
        FOREIGN KEY (target_page_object_id)
        REFERENCES pages (object_id)
        ON DELETE SET NULL,

    CONSTRAINT fk_rich_text_mentions_database
        FOREIGN KEY (target_database_object_id)
        REFERENCES databases (object_id)
        ON DELETE SET NULL,

    CONSTRAINT fk_rich_text_mentions_data_source
        FOREIGN KEY (target_data_source_object_id)
        REFERENCES data_sources (object_id)
        ON DELETE SET NULL
);

CREATE TABLE block_rich_text_fields (
    block_object_id BIGINT NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    rich_text_content_id BIGINT NOT NULL,

    CONSTRAINT pk_block_rich_text_fields
        PRIMARY KEY (block_object_id, field_name),

    CONSTRAINT fk_block_rich_text_fields_block
        FOREIGN KEY (block_object_id)
        REFERENCES blocks (object_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_block_rich_text_fields_content
        FOREIGN KEY (rich_text_content_id)
        REFERENCES rich_text_contents (id)
        ON DELETE CASCADE
);


-- =========================================================
-- 6. Asset / 파일 영구 보존
-- =========================================================

CREATE TABLE assets (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    notion_connection_id BIGINT NOT NULL,

    notion_file_id VARCHAR(100),
    asset_type VARCHAR(30) NOT NULL,
    source_type VARCHAR(30) NOT NULL,

    original_name VARCHAR(500),
    mime_type VARCHAR(255),
    file_size BIGINT,

    storage_key VARCHAR(2048),
    external_url VARCHAR(2048),
    expires_at TIMESTAMPTZ,

    download_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    checksum VARCHAR(128),
    downloaded_at TIMESTAMPTZ,
    download_error TEXT,

    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_assets_connection_file
        UNIQUE (notion_connection_id, notion_file_id),

    CONSTRAINT fk_assets_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_assets_connection
        FOREIGN KEY (notion_connection_id)
        REFERENCES notion_connections (id)
        ON DELETE CASCADE
);

CREATE TABLE page_assets (
    page_object_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    asset_role VARCHAR(30) NOT NULL,

    CONSTRAINT pk_page_assets
        PRIMARY KEY (page_object_id, asset_id, asset_role),

    CONSTRAINT fk_page_assets_page
        FOREIGN KEY (page_object_id)
        REFERENCES pages (object_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_page_assets_asset
        FOREIGN KEY (asset_id)
        REFERENCES assets (id)
        ON DELETE CASCADE
);

CREATE TABLE block_assets (
    block_object_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    asset_role VARCHAR(30) NOT NULL,
    position_index INTEGER NOT NULL DEFAULT 0,
    caption_rich_text_id BIGINT,

    CONSTRAINT pk_block_assets
        PRIMARY KEY (
            block_object_id,
            asset_id,
            asset_role,
            position_index
        ),

    CONSTRAINT fk_block_assets_block
        FOREIGN KEY (block_object_id)
        REFERENCES blocks (object_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_block_assets_asset
        FOREIGN KEY (asset_id)
        REFERENCES assets (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_block_assets_caption
        FOREIGN KEY (caption_rich_text_id)
        REFERENCES rich_text_contents (id)
        ON DELETE SET NULL
);


-- =========================================================
-- 7. Data Source Property Schema
-- =========================================================

CREATE TABLE property_definitions (
    id BIGSERIAL PRIMARY KEY,
    data_source_object_id BIGINT NOT NULL,

    notion_property_id VARCHAR(100) NOT NULL,
    property_name VARCHAR(255) NOT NULL,
    property_type VARCHAR(50) NOT NULL,
    position_index INTEGER NOT NULL DEFAULT 0,

    configuration JSONB,
    raw_payload JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_property_definitions_notion_id
        UNIQUE (data_source_object_id, notion_property_id),

    CONSTRAINT uk_property_definitions_name
        UNIQUE (data_source_object_id, property_name),

    CONSTRAINT fk_property_definitions_data_source
        FOREIGN KEY (data_source_object_id)
        REFERENCES data_sources (object_id)
        ON DELETE CASCADE
);

CREATE TABLE property_option_groups (
    id BIGSERIAL PRIMARY KEY,
    property_definition_id BIGINT NOT NULL,

    notion_group_id VARCHAR(100),
    group_name VARCHAR(255) NOT NULL,
    group_color VARCHAR(30) NOT NULL DEFAULT 'default',
    position_index INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT uk_property_option_groups_notion_id
        UNIQUE (property_definition_id, notion_group_id),

    CONSTRAINT fk_property_option_groups_definition
        FOREIGN KEY (property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE CASCADE
);

CREATE TABLE property_select_options (
    id BIGSERIAL PRIMARY KEY,
    property_definition_id BIGINT NOT NULL,
    option_group_id BIGINT,

    notion_option_id VARCHAR(100) NOT NULL,
    option_name VARCHAR(255) NOT NULL,
    option_color VARCHAR(30) NOT NULL DEFAULT 'default',
    position_index INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT uk_property_select_options_notion_id
        UNIQUE (property_definition_id, notion_option_id),

    CONSTRAINT fk_property_select_options_definition
        FOREIGN KEY (property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_property_select_options_group
        FOREIGN KEY (option_group_id)
        REFERENCES property_option_groups (id)
        ON DELETE SET NULL
);

CREATE TABLE relation_property_configs (
    property_definition_id BIGINT PRIMARY KEY,

    target_data_source_object_id BIGINT,
    target_data_source_notion_id VARCHAR(100) NOT NULL,

    synced_property_definition_id BIGINT,
    synced_property_notion_id VARCHAR(100),

    relation_type VARCHAR(30) NOT NULL,
    raw_payload JSONB,

    CONSTRAINT fk_relation_configs_definition
        FOREIGN KEY (property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_relation_configs_target_source
        FOREIGN KEY (target_data_source_object_id)
        REFERENCES data_sources (object_id)
        ON DELETE SET NULL,

    CONSTRAINT fk_relation_configs_synced_property
        FOREIGN KEY (synced_property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE SET NULL
);

CREATE TABLE formula_property_configs (
    property_definition_id BIGINT PRIMARY KEY,
    formula_expression TEXT,
    result_type VARCHAR(30),
    raw_payload JSONB,

    CONSTRAINT fk_formula_configs_definition
        FOREIGN KEY (property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE CASCADE
);

CREATE TABLE rollup_property_configs (
    property_definition_id BIGINT PRIMARY KEY,

    relation_property_definition_id BIGINT,
    relation_property_notion_id VARCHAR(100),

    target_property_definition_id BIGINT,
    target_property_notion_id VARCHAR(100),

    aggregation_function VARCHAR(50) NOT NULL,
    result_type VARCHAR(30),
    raw_payload JSONB,

    CONSTRAINT fk_rollup_configs_definition
        FOREIGN KEY (property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_rollup_configs_relation
        FOREIGN KEY (relation_property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE SET NULL,

    CONSTRAINT fk_rollup_configs_target
        FOREIGN KEY (target_property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE SET NULL
);


-- =========================================================
-- 8. Page Property Value
-- 단일 값은 공통 테이블, 다중 값은 연결 테이블
-- =========================================================

CREATE TABLE page_property_values (
    id BIGSERIAL PRIMARY KEY,
    page_object_id BIGINT NOT NULL,
    property_definition_id BIGINT NOT NULL,

    notion_value_id VARCHAR(100),
    value_type VARCHAR(50) NOT NULL,
    raw_value JSONB,

    -- 검색/정렬용 추출 컬럼
    rich_text_content_id BIGINT,
    text_value TEXT,
    number_value NUMERIC(38, 10),
    boolean_value BOOLEAN,

    date_start DATE,
    date_end DATE,
    datetime_start TIMESTAMPTZ,
    datetime_end TIMESTAMPTZ,
    time_zone VARCHAR(100),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_page_property_values
        UNIQUE (page_object_id, property_definition_id),

    CONSTRAINT fk_page_property_values_page
        FOREIGN KEY (page_object_id)
        REFERENCES pages (object_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_page_property_values_definition
        FOREIGN KEY (property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_page_property_values_rich_text
        FOREIGN KEY (rich_text_content_id)
        REFERENCES rich_text_contents (id)
        ON DELETE SET NULL
);

CREATE TABLE property_value_options (
    property_value_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    position_index INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT pk_property_value_options
        PRIMARY KEY (property_value_id, option_id),

    CONSTRAINT fk_property_value_options_value
        FOREIGN KEY (property_value_id)
        REFERENCES page_property_values (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_property_value_options_option
        FOREIGN KEY (option_id)
        REFERENCES property_select_options (id)
        ON DELETE CASCADE
);

CREATE TABLE property_value_users (
    property_value_id BIGINT NOT NULL,
    notion_user_id BIGINT NOT NULL,
    position_index INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT pk_property_value_users
        PRIMARY KEY (property_value_id, notion_user_id),

    CONSTRAINT fk_property_value_users_value
        FOREIGN KEY (property_value_id)
        REFERENCES page_property_values (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_property_value_users_user
        FOREIGN KEY (notion_user_id)
        REFERENCES notion_users (id)
        ON DELETE CASCADE
);

CREATE TABLE property_value_relations (
    property_value_id BIGINT NOT NULL,

    target_page_object_id BIGINT,
    target_notion_page_id VARCHAR(100) NOT NULL,
    position_index INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT pk_property_value_relations
        PRIMARY KEY (property_value_id, target_notion_page_id),

    CONSTRAINT fk_property_value_relations_value
        FOREIGN KEY (property_value_id)
        REFERENCES page_property_values (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_property_value_relations_target
        FOREIGN KEY (target_page_object_id)
        REFERENCES pages (object_id)
        ON DELETE SET NULL
);

CREATE TABLE property_value_files (
    property_value_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    position_index INTEGER NOT NULL DEFAULT 0,
    display_name VARCHAR(500),

    CONSTRAINT pk_property_value_files
        PRIMARY KEY (property_value_id, asset_id),

    CONSTRAINT fk_property_value_files_value
        FOREIGN KEY (property_value_id)
        REFERENCES page_property_values (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_property_value_files_asset
        FOREIGN KEY (asset_id)
        REFERENCES assets (id)
        ON DELETE CASCADE
);


-- =========================================================
-- 9. Data Source Template / View
-- =========================================================

CREATE TABLE data_source_templates (
    id BIGSERIAL PRIMARY KEY,
    data_source_object_id BIGINT NOT NULL,

    notion_template_id VARCHAR(100) NOT NULL,
    template_page_object_id BIGINT,

    template_name VARCHAR(255) NOT NULL,
    description TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    position_index INTEGER NOT NULL DEFAULT 0,

    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_data_source_templates_notion_id
        UNIQUE (data_source_object_id, notion_template_id),

    CONSTRAINT fk_data_source_templates_source
        FOREIGN KEY (data_source_object_id)
        REFERENCES data_sources (object_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_data_source_templates_page
        FOREIGN KEY (template_page_object_id)
        REFERENCES pages (object_id)
        ON DELETE SET NULL
);

CREATE TABLE data_source_views (
    id BIGSERIAL PRIMARY KEY,

    notion_view_id VARCHAR(100) NOT NULL,
    database_object_id BIGINT NOT NULL,
    data_source_object_id BIGINT,

    view_name VARCHAR(255) NOT NULL,
    view_type VARCHAR(30) NOT NULL,
    position_index INTEGER NOT NULL DEFAULT 0,

    layout_configuration JSONB,
    raw_payload JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_data_source_views_notion_id
        UNIQUE (notion_view_id),

    CONSTRAINT fk_data_source_views_database
        FOREIGN KEY (database_object_id)
        REFERENCES databases (object_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_data_source_views_data_source
        FOREIGN KEY (data_source_object_id)
        REFERENCES data_sources (object_id)
        ON DELETE SET NULL
);

CREATE TABLE data_source_view_properties (
    view_id BIGINT NOT NULL,
    property_definition_id BIGINT NOT NULL,

    position_index INTEGER NOT NULL DEFAULT 0,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    width INTEGER,

    CONSTRAINT pk_data_source_view_properties
        PRIMARY KEY (view_id, property_definition_id),

    CONSTRAINT fk_data_source_view_properties_view
        FOREIGN KEY (view_id)
        REFERENCES data_source_views (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_data_source_view_properties_definition
        FOREIGN KEY (property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE CASCADE
);

CREATE TABLE data_source_view_sorts (
    id BIGSERIAL PRIMARY KEY,
    view_id BIGINT NOT NULL,
    property_definition_id BIGINT NOT NULL,

    sort_order INTEGER NOT NULL DEFAULT 0,
    direction VARCHAR(10) NOT NULL,
    nulls_order VARCHAR(10) NOT NULL DEFAULT 'LAST',

    CONSTRAINT fk_data_source_view_sorts_view
        FOREIGN KEY (view_id)
        REFERENCES data_source_views (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_data_source_view_sorts_definition
        FOREIGN KEY (property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE CASCADE
);

CREATE TABLE data_source_view_groups (
    id BIGSERIAL PRIMARY KEY,
    view_id BIGINT NOT NULL,
    property_definition_id BIGINT NOT NULL,

    position_index INTEGER NOT NULL DEFAULT 0,
    direction VARCHAR(10) NOT NULL DEFAULT 'ASC',
    hide_empty_groups BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_data_source_view_groups_view
        FOREIGN KEY (view_id)
        REFERENCES data_source_views (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_data_source_view_groups_definition
        FOREIGN KEY (property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE CASCADE
);

CREATE TABLE data_source_view_filter_nodes (
    id BIGSERIAL PRIMARY KEY,
    view_id BIGINT NOT NULL,
    parent_filter_node_id BIGINT,

    node_type VARCHAR(20) NOT NULL,
    logical_operator VARCHAR(10),

    property_definition_id BIGINT,
    filter_operator VARCHAR(40),
    value_type VARCHAR(30),

    string_value TEXT,
    number_value NUMERIC(38, 10),
    boolean_value BOOLEAN,

    date_value DATE,
    datetime_value TIMESTAMPTZ,
    time_zone VARCHAR(100),

    position_index INTEGER NOT NULL DEFAULT 0,
    raw_payload JSONB,

    CONSTRAINT fk_filter_nodes_view
        FOREIGN KEY (view_id)
        REFERENCES data_source_views (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_filter_nodes_parent
        FOREIGN KEY (parent_filter_node_id)
        REFERENCES data_source_view_filter_nodes (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_filter_nodes_definition
        FOREIGN KEY (property_definition_id)
        REFERENCES property_definitions (id)
        ON DELETE SET NULL
);

CREATE TABLE data_source_view_filter_options (
    filter_node_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,

    CONSTRAINT pk_data_source_view_filter_options
        PRIMARY KEY (filter_node_id, option_id),

    CONSTRAINT fk_filter_options_node
        FOREIGN KEY (filter_node_id)
        REFERENCES data_source_view_filter_nodes (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_filter_options_option
        FOREIGN KEY (option_id)
        REFERENCES property_select_options (id)
        ON DELETE CASCADE
);

CREATE TABLE data_source_view_filter_users (
    filter_node_id BIGINT NOT NULL,
    notion_user_id BIGINT NOT NULL,

    CONSTRAINT pk_data_source_view_filter_users
        PRIMARY KEY (filter_node_id, notion_user_id),

    CONSTRAINT fk_filter_users_node
        FOREIGN KEY (filter_node_id)
        REFERENCES data_source_view_filter_nodes (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_filter_users_user
        FOREIGN KEY (notion_user_id)
        REFERENCES notion_users (id)
        ON DELETE CASCADE
);

CREATE TABLE data_source_view_filter_pages (
    filter_node_id BIGINT NOT NULL,

    page_object_id BIGINT,
    notion_page_id VARCHAR(100) NOT NULL,

    CONSTRAINT pk_data_source_view_filter_pages
        PRIMARY KEY (filter_node_id, notion_page_id),

    CONSTRAINT fk_filter_pages_node
        FOREIGN KEY (filter_node_id)
        REFERENCES data_source_view_filter_nodes (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_filter_pages_page
        FOREIGN KEY (page_object_id)
        REFERENCES pages (object_id)
        ON DELETE SET NULL
);


-- =========================================================
-- 10. Comment
-- =========================================================

CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    notion_connection_id BIGINT NOT NULL,

    notion_comment_id VARCHAR(100) NOT NULL,
    parent_object_id BIGINT NOT NULL,
    discussion_id VARCHAR(255),

    created_by_notion_user_id BIGINT,
    rich_text_content_id BIGINT NOT NULL,

    notion_created_at TIMESTAMPTZ,
    notion_updated_at TIMESTAMPTZ,
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,

    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_comments_connection_comment
        UNIQUE (notion_connection_id, notion_comment_id),

    CONSTRAINT fk_comments_connection
        FOREIGN KEY (notion_connection_id)
        REFERENCES notion_connections (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_comments_parent_object
        FOREIGN KEY (parent_object_id)
        REFERENCES objects (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_comments_created_by
        FOREIGN KEY (created_by_notion_user_id)
        REFERENCES notion_users (id)
        ON DELETE SET NULL,

    CONSTRAINT fk_comments_rich_text
        FOREIGN KEY (rich_text_content_id)
        REFERENCES rich_text_contents (id)
        ON DELETE CASCADE
);


-- =========================================================
-- 11. Page Link Projection
-- Rich Text Mention / Relation에서 재생성 가능
-- =========================================================

CREATE TABLE page_links (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    notion_connection_id BIGINT NOT NULL,

    source_page_object_id BIGINT NOT NULL,
    target_page_object_id BIGINT,
    target_notion_page_id VARCHAR(100) NOT NULL,

    source_block_object_id BIGINT,
    source_segment_id BIGINT,

    link_type VARCHAR(30) NOT NULL,
    generated_from VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_page_links_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_page_links_connection
        FOREIGN KEY (notion_connection_id)
        REFERENCES notion_connections (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_page_links_source_page
        FOREIGN KEY (source_page_object_id)
        REFERENCES pages (object_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_page_links_target_page
        FOREIGN KEY (target_page_object_id)
        REFERENCES pages (object_id)
        ON DELETE SET NULL,

    CONSTRAINT fk_page_links_source_block
        FOREIGN KEY (source_block_object_id)
        REFERENCES blocks (object_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_page_links_source_segment
        FOREIGN KEY (source_segment_id)
        REFERENCES rich_text_segments (id)
        ON DELETE CASCADE
);


-- =========================================================
-- 12. Import / Sync / Webhook 운영
-- =========================================================

CREATE TABLE notion_sync_roots (
    id BIGSERIAL PRIMARY KEY,
    notion_connection_id BIGINT NOT NULL,

    notion_object_id VARCHAR(100) NOT NULL,
    object_type VARCHAR(30) NOT NULL,
    local_object_id BIGINT,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_notion_sync_roots
        UNIQUE (notion_connection_id, notion_object_id),

    CONSTRAINT fk_notion_sync_roots_connection
        FOREIGN KEY (notion_connection_id)
        REFERENCES notion_connections (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_notion_sync_roots_object
        FOREIGN KEY (local_object_id)
        REFERENCES objects (id)
        ON DELETE SET NULL
);

CREATE TABLE sync_jobs (
    id BIGSERIAL PRIMARY KEY,
    notion_connection_id BIGINT NOT NULL,

    sync_type VARCHAR(30) NOT NULL,
    sync_status VARCHAR(30) NOT NULL,

    total_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,

    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_message TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_sync_jobs_connection
        FOREIGN KEY (notion_connection_id)
        REFERENCES notion_connections (id)
        ON DELETE CASCADE
);

CREATE TABLE sync_job_items (
    id BIGSERIAL PRIMARY KEY,
    sync_job_id BIGINT NOT NULL,

    notion_object_id VARCHAR(100) NOT NULL,
    object_type VARCHAR(30) NOT NULL,
    local_object_id BIGINT,

    sync_status VARCHAR(30) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,

    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,

    CONSTRAINT uk_sync_job_items
        UNIQUE (sync_job_id, notion_object_id),

    CONSTRAINT fk_sync_job_items_job
        FOREIGN KEY (sync_job_id)
        REFERENCES sync_jobs (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_sync_job_items_object
        FOREIGN KEY (local_object_id)
        REFERENCES objects (id)
        ON DELETE SET NULL
);

CREATE TABLE object_sync_states (
    object_id BIGINT PRIMARY KEY,

    notion_last_edited_at TIMESTAMPTZ,
    last_synced_at TIMESTAMPTZ,

    content_hash VARCHAR(128),
    sync_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error_message TEXT,
    access_lost_at TIMESTAMPTZ,

    CONSTRAINT fk_object_sync_states_object
        FOREIGN KEY (object_id)
        REFERENCES objects (id)
        ON DELETE CASCADE
);

CREATE TABLE notion_object_snapshots (
    id BIGSERIAL PRIMARY KEY,
    notion_connection_id BIGINT NOT NULL,

    notion_object_id VARCHAR(100) NOT NULL,
    object_type VARCHAR(30) NOT NULL,
    api_version VARCHAR(30) NOT NULL,

    raw_payload JSONB NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_snapshots_connection
        FOREIGN KEY (notion_connection_id)
        REFERENCES notion_connections (id)
        ON DELETE CASCADE
);

CREATE TABLE webhook_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    notion_connection_id BIGINT NOT NULL,

    notion_subscription_id VARCHAR(100),
    callback_url VARCHAR(2048) NOT NULL,
    encrypted_verification_token TEXT,

    subscription_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    verified_at TIMESTAMPTZ,
    disabled_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_webhook_subscriptions_notion_id
        UNIQUE (notion_subscription_id),

    CONSTRAINT fk_webhook_subscriptions_connection
        FOREIGN KEY (notion_connection_id)
        REFERENCES notion_connections (id)
        ON DELETE CASCADE
);

CREATE TABLE webhook_events (
    id BIGSERIAL PRIMARY KEY,
    webhook_subscription_id BIGINT NOT NULL,

    notion_event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,

    entity_type VARCHAR(30),
    entity_notion_id VARCHAR(100),

    attempt_number INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,

    processing_status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    error_message TEXT,

    CONSTRAINT uk_webhook_events_notion_id
        UNIQUE (notion_event_id),

    CONSTRAINT fk_webhook_events_subscription
        FOREIGN KEY (webhook_subscription_id)
        REFERENCES webhook_subscriptions (id)
        ON DELETE CASCADE
);


-- =========================================================
-- 13. 프론트 조회용 Projection
-- =========================================================

CREATE TABLE page_render_snapshots (
    page_object_id BIGINT PRIMARY KEY,

    version BIGINT NOT NULL DEFAULT 1,
    document_json JSONB NOT NULL,

    source_updated_at TIMESTAMPTZ NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_page_render_snapshots_page
        FOREIGN KEY (page_object_id)
        REFERENCES pages (object_id)
        ON DELETE CASCADE
);


-- =========================================================
-- 14. 주요 인덱스
-- =========================================================

CREATE INDEX idx_workspace_members_user
    ON workspace_members (user_id);

CREATE INDEX idx_notion_connections_workspace
    ON notion_connections (workspace_id);

CREATE INDEX idx_notion_users_connection
    ON notion_users (notion_connection_id);

CREATE INDEX idx_objects_workspace_type
    ON objects (workspace_id, object_type);

CREATE INDEX idx_objects_connection_type
    ON objects (notion_connection_id, object_type);

CREATE INDEX idx_objects_parent_position
    ON objects (parent_object_id, position_index);

CREATE INDEX idx_objects_external_parent
    ON objects (
        notion_connection_id,
        external_parent_type,
        external_parent_notion_id
    );

CREATE INDEX idx_objects_parent_database_notion
    ON objects (notion_connection_id, parent_database_notion_id);

CREATE INDEX idx_objects_notion_updated_at
    ON objects (notion_connection_id, notion_updated_at);

CREATE INDEX idx_pages_plain_title
    ON pages (plain_title);

CREATE INDEX idx_blocks_block_type
    ON blocks (block_type);

CREATE INDEX idx_rich_text_segments_content_position
    ON rich_text_segments (rich_text_content_id, position_index);

CREATE INDEX idx_assets_download_status
    ON assets (download_status);

CREATE INDEX idx_property_definitions_source_position
    ON property_definitions (data_source_object_id, position_index);

CREATE INDEX idx_page_property_values_page
    ON page_property_values (page_object_id);

CREATE INDEX idx_page_property_values_definition
    ON page_property_values (property_definition_id);

CREATE INDEX idx_page_property_values_text
    ON page_property_values (text_value);

CREATE INDEX idx_page_property_values_number
    ON page_property_values (number_value);

CREATE INDEX idx_page_property_values_date
    ON page_property_values (date_start);

CREATE INDEX idx_page_property_values_datetime
    ON page_property_values (datetime_start);

CREATE INDEX idx_property_value_relations_target
    ON property_value_relations (target_notion_page_id);

CREATE INDEX idx_data_source_views_database
    ON data_source_views (database_object_id);

CREATE INDEX idx_page_links_source
    ON page_links (source_page_object_id);

CREATE INDEX idx_page_links_target
    ON page_links (target_notion_page_id);

CREATE INDEX idx_sync_jobs_connection_status
    ON sync_jobs (notion_connection_id, sync_status);

CREATE INDEX idx_snapshots_object_fetched
    ON notion_object_snapshots (
        notion_connection_id,
        notion_object_id,
        fetched_at
    );

CREATE INDEX idx_webhook_events_status
    ON webhook_events (processing_status, received_at);
