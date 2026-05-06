ALTER TABLE users
    ADD COLUMN password VARCHAR(255),
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER';

UPDATE users
SET password = '$2a$10$FZg.qafmxCMf7RDTjvlUYeZpmsv1ztAj2EniZujgtu3ggVfKG6eum'
WHERE password IS NULL;

ALTER TABLE users
    ALTER COLUMN password SET NOT NULL,
    ALTER COLUMN role DROP DEFAULT;
