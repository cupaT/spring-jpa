CREATE TABLE dishes (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    price NUMERIC(14, 2) NOT NULL,
    is_available BOOLEAN NOT NULL,
    restaurant_id BIGINT NOT NULL,
    CONSTRAINT fk_dishes_restaurant
        FOREIGN KEY (restaurant_id)
        REFERENCES restaurants(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_dishes_restaurant_id ON dishes(restaurant_id);
