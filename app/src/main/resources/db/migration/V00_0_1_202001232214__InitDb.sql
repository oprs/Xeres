--
-- Database creation
--
-- Avoid touching this file if unnecessary (even comments) as this will trigger
-- a flyway migration. Migrations will be consistently used and this file won't
-- be touched anymore when we reach 1.0.0
--
-- See https://h2database.com/html/datatypes.html for the data types
--
CREATE TABLE profiles
(
	id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	name                VARCHAR(64) NOT NULL,
	pgp_identifier      BIGINT      NOT NULL UNIQUE,
	pgp_fingerprint     BINARY(20)  NOT NULL,
	pgp_public_key_data VARBINARY(16384),
	accepted            BOOLEAN     NOT NULL                                      DEFAULT false,
	trust               ENUM ('unknown', 'never', 'marginal', 'full', 'ultimate') DEFAULT 'unknown'
);

CREATE TABLE locations
(
	id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	profile_id          BIGINT      NOT NULL,
	name                VARCHAR(64) NOT NULL,
	location_identifier BINARY(16)  NOT NULL UNIQUE,
	connected           BOOLEAN     NOT NULL                                            DEFAULT false,
	discoverable        BOOLEAN     NOT NULL                                            DEFAULT true,
	dht                 BOOLEAN     NOT NULL                                            DEFAULT true,
	net_mode            ENUM ('unknown', 'udp', 'upnp', 'ext', 'hidden', 'unreachable') DEFAULT 'unknown',
	last_connected      TIMESTAMP,
	CONSTRAINT fk_location_profile FOREIGN KEY (profile_id) REFERENCES profiles (id) ON DELETE CASCADE
);

CREATE TABLE connections
(
	id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	location_id    BIGINT       NOT NULL,
	type           ENUM ('invalid', 'ipv4', 'ipv6', 'tor', 'i2p'),
	address        VARCHAR(128) NOT NULL,
	last_connected TIMESTAMP,
	external       BOOLEAN      NOT NULL,
	CONSTRAINT fk_connection_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE
);

CREATE TABLE prefs
(
	lock                      TINYINT NOT NULL DEFAULT 1,

	pgp_private_key_data      VARBINARY(16384) DEFAULT NULL,

	location_private_key_data VARBINARY(16384) DEFAULT NULL,
	location_public_key_data  VARBINARY(16384) DEFAULT NULL,
	location_certificate      VARBINARY(16384) DEFAULT NULL,

	CONSTRAINT pk_t1 PRIMARY KEY (lock),
	CONSTRAINT ck_t1_locked CHECK (lock = 1)
);
INSERT INTO prefs (lock)
VALUES (1);

CREATE TABLE gxs_client_updates
(
	id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	location_id  BIGINT NOT NULL,
	service_type INT    NOT NULL,
	last_synced  TIMESTAMP,
	CONSTRAINT fk_gxs_client_update_location FOREIGN KEY (location_id) REFERENCES locations (id) ON DELETE CASCADE
);
CREATE INDEX idx_location_service ON gxs_client_updates (location_id, service_type);

CREATE TABLE gxs_service_settings
(
	id           INT PRIMARY KEY NOT NULL,
	last_updated TIMESTAMP
);

CREATE TABLE gxs_groups
(
	id                     BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	gxs_id                 BINARY(16)   NOT NULL UNIQUE,
	original_gxs_id        BINARY(16),
	name                   VARCHAR(512) NOT NULL,
	diffusion_flags        INT          NOT NULL                                                                                   DEFAULT 0,
	signature_flags        INT          NOT NULL                                                                                   DEFAULT 0,
	published              TIMESTAMP,
	author                 BINARY(16),
	circle_id              BINARY(16),
	circle_type            ENUM ('unknown', 'public', 'external', 'your_friends_only', 'local', 'external_self', 'your_eyes_only') DEFAULT 'unknown',
	authentication_flags   INT          NOT NULL                                                                                   DEFAULT 0,
	parent_id              BINARY(16),
	subscribe_flags        INT          NOT NULL                                                                                   DEFAULT 0,
	popularity             INT          NOT NULL                                                                                   DEFAULT 0,
	visible_message_count  INT          NOT NULL                                                                                   DEFAULT 0,
	last_posted            TIMESTAMP,
	status                 INT          NOT NULL                                                                                   DEFAULT 0,
	service_string         VARCHAR(512),
	originator             BINARY(16),
	internal_circle        BINARY(16),
	admin_private_key      VARBINARY(16384),
	admin_public_key       VARBINARY(16384),
	publishing_private_key VARBINARY(16384),
	publishing_public_key  VARBINARY(16384)
);

CREATE TABLE identity_groups
(
	id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	profile_hash      BINARY(20),
	profile_signature VARBINARY(2048),
	image             VARBINARY(65536)                          DEFAULT NULL,
	type              ENUM ('other', 'own', 'friend', 'banned') DEFAULT 'other'
);
CREATE INDEX idx_type ON identity_groups (type);

CREATE TABLE chatrooms
(
	id                BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
	room_id           BIGINT       NOT NULL,
	identity_group_id BIGINT       NOT NULL,
	name              VARCHAR(256) NOT NULL,
	topic             VARCHAR(256) NOT NULL,
	flags             INT          NOT NULL DEFAULT 0,
	subscribed        BOOLEAN      NOT NULL DEFAULT true,
	joined            BOOLEAN      NOT NULL DEFAULT false
);