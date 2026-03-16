CREATE TABLE order_dishes (
    order_id BIGINT NOT NULL,
    dish_id BIGINT NOT NULL,
    PRIMARY KEY (order_id, dish_id),
    CONSTRAINT fk_order_dishes_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_order_dishes_dish
        FOREIGN KEY (dish_id)
        REFERENCES dishes(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_order_dishes_dish_id ON order_dishes(dish_id);
