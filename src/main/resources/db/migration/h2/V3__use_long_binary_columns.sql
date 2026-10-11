-- Match the BLOB mapping used for image payloads. This type can store
-- the configured 10 MB uploads; MySQL uses LONGBLOB in its vendor baseline.
ALTER TABLE books ALTER COLUMN book_cover_file SET DATA TYPE BINARY VARYING(10000000);
ALTER TABLE person ALTER COLUMN profile_picture SET DATA TYPE BINARY VARYING(10000000);
