CREATE TABLE shared_wheel (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL,
    auto_remove BOOLEAN NOT NULL,
    expires_at TIMESTAMPTZ,
    CONSTRAINT shared_wheel_name_trimmed CHECK (name = btrim(name)),
    CONSTRAINT shared_wheel_name_not_blank CHECK (char_length(name) BETWEEN 1 AND 80),
    CONSTRAINT shared_wheel_version_non_negative CHECK (version >= 0)
);

CREATE TABLE shared_wheel_member (
    id UUID PRIMARY KEY,
    wheel_id UUID NOT NULL REFERENCES shared_wheel(id) ON DELETE CASCADE,
    roster_position INTEGER NOT NULL,
    name VARCHAR(80) NOT NULL,
    eligible BOOLEAN NOT NULL,
    CONSTRAINT shared_wheel_member_position_range CHECK (roster_position BETWEEN 0 AND 99),
    CONSTRAINT shared_wheel_member_name_trimmed CHECK (name = btrim(name)),
    CONSTRAINT shared_wheel_member_name_not_blank CHECK (char_length(name) BETWEEN 1 AND 80),
    CONSTRAINT shared_wheel_member_position_unique UNIQUE (wheel_id, roster_position),
    CONSTRAINT shared_wheel_member_name_unique UNIQUE (wheel_id, name)
);
