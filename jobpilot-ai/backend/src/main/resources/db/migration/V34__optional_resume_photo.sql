ALTER TABLE member_specifications
    ADD COLUMN profile_photo LONGBLOB NULL,
    ADD COLUMN profile_photo_content_type VARCHAR(40) NULL;
