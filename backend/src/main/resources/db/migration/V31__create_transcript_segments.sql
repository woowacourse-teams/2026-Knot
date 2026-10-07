CREATE TABLE transcript_segments (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    transcript_id BIGINT NOT NULL,
    position INTEGER NOT NULL,
    start_millis BIGINT NOT NULL,
    end_millis BIGINT,
    speaker_number INTEGER,
    text TEXT NOT NULL,
    CONSTRAINT pk_transcript_segments PRIMARY KEY (id),
    CONSTRAINT fk_transcript_segments_transcript FOREIGN KEY (transcript_id)
        REFERENCES transcripts (id) ON DELETE RESTRICT,
    CONSTRAINT uk_transcript_segments_transcript_position UNIQUE (transcript_id, position),
    CONSTRAINT chk_transcript_segments_position CHECK (position >= 0),
    CONSTRAINT chk_transcript_segments_start CHECK (start_millis >= 0),
    CONSTRAINT chk_transcript_segments_end CHECK (end_millis IS NULL OR end_millis >= start_millis),
    CONSTRAINT chk_transcript_segments_speaker CHECK (speaker_number IS NULL OR speaker_number >= 1),
    -- Java String.isBlank와 같은 공백 범위를 사용해 DB locale에 따라 빈 문장 허용이 달라지지 않게 한다.
    CONSTRAINT chk_transcript_segments_text CHECK (
        length(btrim(text, U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000')) > 0
    )
);

CREATE INDEX idx_transcript_segments_transcript_start_position
    ON transcript_segments (transcript_id, start_millis, position);
