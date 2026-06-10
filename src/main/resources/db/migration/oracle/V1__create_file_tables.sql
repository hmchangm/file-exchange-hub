CREATE TABLE file_metadata (
    id            VARCHAR2(36)   NOT NULL PRIMARY KEY,
    bucket        VARCHAR2(255)  NOT NULL,
    report_id     VARCHAR2(255)  NOT NULL,
    report_category VARCHAR2(255) NOT NULL,
    object_key    VARCHAR2(2000) NOT NULL,
    filename      VARCHAR2(255)  NOT NULL,
    content_type  VARCHAR2(128)  NOT NULL,
    file_size     NUMBER(19)     NOT NULL,
    checksum      VARCHAR2(256),
    uploader_id   VARCHAR2(255)  NOT NULL,
    tags          VARCHAR2(4000) CHECK (tags IS JSON),
    status        VARCHAR2(10) DEFAULT 'REGISTERED' NOT NULL
                  CHECK (status IN ('REGISTERED','FAILED')),
    remark        VARCHAR2(1024),
    error_code    VARCHAR2(64),
    registered_at TIMESTAMP(6)   NOT NULL
);

CREATE INDEX idx_file_metadata_query
    ON file_metadata (registered_at, bucket, status);

CREATE TABLE file_delivery (
    id           VARCHAR2(36)  NOT NULL,
    file_id      VARCHAR2(36)  NOT NULL,
    consumer_id  VARCHAR2(255) NOT NULL,
    note         CLOB,
    processed_at TIMESTAMP(6)  NOT NULL,
    CONSTRAINT pk_file_delivery PRIMARY KEY (id),
    CONSTRAINT fk_delivery_file FOREIGN KEY (file_id) REFERENCES file_metadata(id),
    CONSTRAINT uq_delivery UNIQUE (file_id, consumer_id)
);

CREATE INDEX idx_file_delivery_file_id ON file_delivery (file_id);
