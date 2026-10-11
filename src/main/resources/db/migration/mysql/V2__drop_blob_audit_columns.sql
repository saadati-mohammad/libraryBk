-- Remove the binary payload columns from the Envers audit tables.
--
-- V1 declared books_aud.book_cover_file and person_aud.profile_picture as
-- VARBINARY(255), while the live tables store a BLOB. Any upload larger than 255
-- bytes therefore failed on the audit insert ("Value too long for column
-- BOOK_COVER_FILE BINARY VARYING(255)") and surfaced as HTTP 500.
--
-- The entities now annotate both fields with @NotAudited, so Envers no longer writes
-- them; this migration drops the now-unused columns so the audit schema matches the
-- mapping. V1 is left untouched because it has already been applied in real
-- deployments and Flyway checksums are immutable.
--
-- Portable syntax: both H2 and MySQL accept `ALTER TABLE <t> DROP COLUMN <c>`.

ALTER TABLE books_aud DROP COLUMN book_cover_file;
ALTER TABLE person_aud DROP COLUMN profile_picture;
