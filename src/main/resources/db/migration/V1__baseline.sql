-- ============================================================
-- V1__baseline.sql
-- Baseline migration — schema verificado contra SHOW CREATE TABLE.
-- ENUM values citados textualmente de real_schema.sql.
-- Flyway marca esto como aplicado sin ejecutarlo
-- (spring.flyway.baseline-on-migrate=true, baseline-version=1).
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    phone      VARCHAR(255),
    role       ENUM('ADMIN','OWNER','PLAYER'),
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS owners (
    id            BIGINT       NOT NULL,
    dni           BIGINT       NOT NULL,
    cuil          VARCHAR(255),
    date_of_birth DATE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_owners_cuil (cuil),
    CONSTRAINT fk_owners_users FOREIGN KEY (id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS players (
    id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_players_users FOREIGN KEY (id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS address (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    street_name VARCHAR(255),
    number      INT      NOT NULL,
    city        VARCHAR(255),
    province    VARCHAR(255),
    code        INT      DEFAULT NULL,
    latitude    DOUBLE   NOT NULL,
    longitude   DOUBLE   NOT NULL,
    is_default  BIT(1)   NOT NULL,
    id_player   BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_address_player FOREIGN KEY (id_player) REFERENCES players (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS clubs (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    name                VARCHAR(255),
    legal_name          VARCHAR(255),
    cuit                VARCHAR(255),
    club_type           ENUM('ASOCIACION_CIVIL','SA','SAS','SRL','UNIPERSONAL'),
    verification_status ENUM('APPROVED','PENDING','REJECTED'),
    active_status       ENUM('ACTIVE','INACTIVE'),
    PRIMARY KEY (id),
    UNIQUE KEY uq_clubs_cuit (cuit),
    UNIQUE KEY uq_clubs_legal_name (legal_name),
    INDEX idx_clubs_verification_active (verification_status, active_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS owner_club (
    owner_id BIGINT NOT NULL,
    club_id  BIGINT NOT NULL,
    -- Sin PRIMARY KEY: refleja el estado real de la DB.
    -- V2 agrega PRIMARY KEY (owner_id, club_id) previa limpieza de duplicados.
    CONSTRAINT fk_owner_club_owner FOREIGN KEY (owner_id) REFERENCES owners (id),
    CONSTRAINT fk_owner_club_club  FOREIGN KEY (club_id)  REFERENCES clubs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS branches (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    name                VARCHAR(255),
    address_id          BIGINT,
    club_id             BIGINT       NOT NULL,
    verification_status ENUM('APPROVED','PENDING','REJECTED'),
    active_status       ENUM('ACTIVE','INACTIVE'),
    PRIMARY KEY (id),
    UNIQUE KEY uq_branches_address_id (address_id),
    CONSTRAINT fk_branches_address FOREIGN KEY (address_id) REFERENCES address (id),
    CONSTRAINT fk_branches_club    FOREIGN KEY (club_id)    REFERENCES clubs (id),
    INDEX idx_branches_club_verification_active (club_id, verification_status, active_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sport (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name_sport VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS court (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(255),
    price_per_hour DOUBLE       NOT NULL,
    branch_id      BIGINT,
    sport_id       BIGINT,
    active_status  ENUM('ACTIVE','INACTIVE'),
    PRIMARY KEY (id),
    CONSTRAINT fk_court_branch FOREIGN KEY (branch_id) REFERENCES branches (id),
    CONSTRAINT fk_court_sport  FOREIGN KEY (sport_id)  REFERENCES sport (id),
    INDEX idx_court_branch_active (branch_id, active_status),
    INDEX idx_court_branch_sport  (branch_id, sport_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS availability (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    day_of_week   ENUM('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY'),
    opening_time  TIME(6),
    closing_time  TIME(6),
    slot_duration BIGINT,
    court_id      BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_availability_court FOREIGN KEY (court_id) REFERENCES court (id),
    INDEX idx_availability_court_day (court_id, day_of_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reservation (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    reservation_day DATE,
    start_time      TIME(6),
    duration        BIGINT,
    status          ENUM('CANCELADO','FINALIZADO','REPROGRAMADO','RESERVADO'),
    court_id        BIGINT,
    player_id       BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_reservation_court  FOREIGN KEY (court_id)  REFERENCES court (id),
    CONSTRAINT fk_reservation_player FOREIGN KEY (player_id) REFERENCES players (id),
    INDEX idx_reservation_court_day_status (court_id, reservation_day, status),
    INDEX idx_reservation_court_status     (court_id, status),
    INDEX idx_reservation_player           (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tickets (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    reservation_id   BIGINT,
    player_name      VARCHAR(255),
    player_last_name VARCHAR(255),
    court_name       VARCHAR(255),
    sport            VARCHAR(255),
    branch_name      VARCHAR(255),
    branch_address   VARCHAR(255),
    total_price      DOUBLE,
    issued_at        DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_tickets_reservation_id (reservation_id),
    CONSTRAINT fk_tickets_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;