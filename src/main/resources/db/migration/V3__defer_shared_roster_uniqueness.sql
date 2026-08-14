ALTER TABLE shared_wheel_member
    DROP CONSTRAINT shared_wheel_member_position_unique,
    ADD CONSTRAINT shared_wheel_member_position_unique
        UNIQUE (wheel_id, roster_position) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE shared_wheel_member
    DROP CONSTRAINT shared_wheel_member_name_unique,
    ADD CONSTRAINT shared_wheel_member_name_unique
        UNIQUE (wheel_id, name) DEFERRABLE INITIALLY DEFERRED;
