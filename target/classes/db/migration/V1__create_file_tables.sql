CREATE TABLE file_metadata (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    bucket        VARCHAR(255)  NOT NULL,
    report_id     VARCHAR(255)  NOT NULL,
    report_category VARCHAR(255) NOT NULL,
    object_key    VARCHAR(2000) NOT NULL,
    filename      VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(128)  NOT NULL,
    file_size     BIGINT        NOT NULL,
    checksum      VARCHAR(256)  NULL,
    uploader_id   VARCHAR(255)  NOT NULL,
    tags          JSON          NULL,
    status        ENUM('REGISTERED','FAILED') NOT NULL DEFAULT 'REGISTERED',
    remark        VARCHAR(1024) NULL,
    error_code    VARCHAR(64)   NULL,
    registered_at DATETIME(6)   NOT NULL
);

CREATE INDEX idx_file_metadata_query
    ON file_metadata (registered_at, bucket, status);

CREATE TABLE file_delivery (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    file_id      VARCHAR(36)  NOT NULL,
    consumer_id  VARCHAR(255) NOT NULL,
    note         TEXT         NULL,
    processed_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_delivery_file FOREIGN KEY (file_id) REFERENCES file_metadata(id),
    CONSTRAINT uq_delivery UNIQUE (file_id, consumer_id)
);

CREATE INDEX idx_file_delivery_file_id ON file_delivery (file_id);
