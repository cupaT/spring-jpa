CREATE TABLE processed_events (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    new_status VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_processed_events_order_status UNIQUE (order_id, new_status)
);
