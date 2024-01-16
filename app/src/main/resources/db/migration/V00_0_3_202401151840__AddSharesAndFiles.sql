--
-- Add shares and files
--
CREATE TABLE file
(
	id        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	parent_id BIGINT DEFAULT NULL,
	name      VARCHAR(255) NOT NULL,
	type      ENUM ('any', 'audio', 'archive', 'cdimage', 'document', 'picture', 'program', 'video', 'directory') DEFAULT 'any',
	hash      BINARY(20),
	modified  TIMESTAMP,

	CONSTRAINT fk_file_parent FOREIGN KEY (parent_id) REFERENCES file (id)
);
CREATE INDEX idx_parent_name ON file (parent_id, name);
CREATE INDEX idx_hash ON file (hash);

CREATE TABLE share
(
	id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	file_id    BIGINT NOT NULL,
	name       VARCHAR(64) NOT NULL,
	searchable BOOLEAN NOT NULL DEFAULT false,
	browsable  ENUM ('unknown', 'never', 'marginal', 'full', 'ultimate') DEFAULT 'unknown',

	CONSTRAINT fk_share_file FOREIGN KEY (file_id) REFERENCES file (id)
);