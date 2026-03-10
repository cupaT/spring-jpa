#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/../.env"
if [ -f "$ENV_FILE" ]; then
    set -a
    . "$ENV_FILE"
    set +a
fi

BASE_URL="${BASE_URL:-http://localhost:${SERVER_PORT:-8080}}"
CONTENT_TYPE="Content-Type: application/json"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

if ! command -v jq &> /dev/null; then
    echo -e "${RED}Ошибка: утилита jq не установлена.${NC}"
    echo "Установите её (sudo apt install jq / brew install jq) и попробуйте снова."
    exit 1
fi

call_api() {
    local method="$1"
    local path="$2"
    local payload="${3:-}"
    local tmp_file

    tmp_file="$(mktemp)"

    if [ -n "$payload" ]; then
        API_STATUS=$(curl -s -o "$tmp_file" -w "%{http_code}" -X "$method" \
            -H "$CONTENT_TYPE" \
            "$BASE_URL$path" \
            -d "$payload")
    else
        API_STATUS=$(curl -s -o "$tmp_file" -w "%{http_code}" -X "$method" \
            "$BASE_URL$path")
    fi

    API_BODY=$(cat "$tmp_file")
    rm -f "$tmp_file"
}

assert_status() {
    local expected="$1"
    if [ "$API_STATUS" != "$expected" ]; then
        echo -e "${RED}FAIL${NC}"
        echo "  Ожидался статус: $expected"
        echo "  Получен статус:  $API_STATUS"
        echo "  Тело ответа: $API_BODY"
        exit 1
    fi
}

assert_json_field_exists() {
    local field="$1"
    if ! echo "$API_BODY" | jq -e ".${field} != null" > /dev/null; then
        echo -e "${RED}FAIL${NC}"
        echo "  В ответе отсутствует поле '${field}'"
        echo "  Тело ответа: $API_BODY"
        exit 1
    fi
}

echo "Ожидаю запуск сервера на $BASE_URL..."
RETRIES=30 # 30 * 2 сек = до 1 минуты
count=0

until curl -s "$BASE_URL/api/v1/users" > /dev/null; do
    count=$((count + 1))
    if [ $count -ge $RETRIES ]; then
        echo -e "${RED}Ошибка: сервер не запустился за минуту.${NC}"
        exit 1
    fi
    echo "Ждём запуска... ($count/$RETRIES)"
    sleep 2
done

echo -e "${GREEN}Сервер доступен! Начинаем тесты.${NC}"
echo "Запуск тестов API по спецификации lab/4/spec.yaml..."
echo "---------------------------------------------------"

SUFFIX="$(date +%s)-$$"
USER_EMAIL="ci-user-${SUFFIX}@example.com"
RESTAURANT_NAME="CI-Restaurant-${SUFFIX}"
RESTAURANT2_NAME="CI-Restaurant2-${SUFFIX}"
DISH_NAME="CI-Dish-${SUFFIX}"
DISH2_NAME="CI-Dish2-${SUFFIX}"
DISH3_NAME="CI-Dish3-${SUFFIX}"

# ==========================================
# USERS
# ==========================================

USER_CREATE_PAYLOAD="{\"email\":\"${USER_EMAIL}\",\"firstName\":\"Ivan\",\"lastName\":\"Petrov\",\"isActive\":true}"
USER_UPDATE_PAYLOAD="{\"email\":\"${USER_EMAIL}\",\"firstName\":\"IvanUpdated\",\"lastName\":\"PetrovUpdated\",\"isActive\":false}"

echo -n "Тест 1: GET /api/v1/users -> 200 и массив... "
call_api "GET" "/api/v1/users"
assert_status "200"
if ! echo "$API_BODY" | jq -e 'type == "array"' > /dev/null; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался массив JSON"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 2: POST /api/v1/users (новый) -> 201... "
call_api "POST" "/api/v1/users" "$USER_CREATE_PAYLOAD"
assert_status "201"
assert_json_field_exists "id"
assert_json_field_exists "email"
assert_json_field_exists "firstName"
assert_json_field_exists "lastName"
USER_ID=$(echo "$API_BODY" | jq -r '.id')
if [ "$USER_ID" = "null" ] || [ -z "$USER_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Не удалось извлечь user id: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 3: POST /api/v1/users (существующий) -> 200... "
call_api "POST" "/api/v1/users" "$USER_CREATE_PAYLOAD"
assert_status "200"
POST_USER_ID=$(echo "$API_BODY" | jq -r '.id')
if [ "$POST_USER_ID" != "$USER_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался тот же id: $USER_ID, получен: $POST_USER_ID"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 4: POST /api/v1/users (невалидный body) -> 400... "
call_api "POST" "/api/v1/users" "{}"
assert_status "400"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 5: GET /api/v1/users/{id} -> 200... "
call_api "GET" "/api/v1/users/$USER_ID"
assert_status "200"
GOT_EMAIL=$(echo "$API_BODY" | jq -r '.email')
if [ "$GOT_EMAIL" != "$USER_EMAIL" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался email: $USER_EMAIL"
    echo "  Получен email:  $GOT_EMAIL"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 6: PUT /api/v1/users/{id} -> 200... "
call_api "PUT" "/api/v1/users/$USER_ID" "$USER_UPDATE_PAYLOAD"
assert_status "200"
UPDATED_FIRST_NAME=$(echo "$API_BODY" | jq -r '.firstName')
UPDATED_LAST_NAME=$(echo "$API_BODY" | jq -r '.lastName')
UPDATED_ACTIVE=$(echo "$API_BODY" | jq -r '.isActive')
if [ "$UPDATED_FIRST_NAME" != "IvanUpdated" ] || [ "$UPDATED_LAST_NAME" != "PetrovUpdated" ] || [ "$UPDATED_ACTIVE" != "false" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Не применилось обновление пользователя"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 7: PUT /api/v1/users/{id} (невалидный body) -> 400... "
call_api "PUT" "/api/v1/users/$USER_ID" "{}"
assert_status "400"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 8: GET /api/v1/users/999999 -> 404... "
call_api "GET" "/api/v1/users/999999"
assert_status "404"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

# ==========================================
# RESTAURANTS
# ==========================================

RESTAURANT_CREATE_PAYLOAD="{\"name\":\"${RESTAURANT_NAME}\",\"address\":\"ул. Пушкина, д. 10\"}"
RESTAURANT_UPDATE_PAYLOAD="{\"name\":\"${RESTAURANT_NAME}-Updated\",\"address\":\"ул. Лермонтова, д. 5\"}"
RESTAURANT2_CREATE_PAYLOAD="{\"name\":\"${RESTAURANT2_NAME}\",\"address\":\"пр. Невский, д. 100\"}"

echo -n "Тест 9: GET /api/v1/restaurants -> 200 и массив... "
call_api "GET" "/api/v1/restaurants"
assert_status "200"
if ! echo "$API_BODY" | jq -e 'type == "array"' > /dev/null; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался массив JSON"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 10: POST /api/v1/restaurants -> 201... "
call_api "POST" "/api/v1/restaurants" "$RESTAURANT_CREATE_PAYLOAD"
assert_status "201"
assert_json_field_exists "id"
assert_json_field_exists "name"
assert_json_field_exists "address"
RESTAURANT_ID=$(echo "$API_BODY" | jq -r '.id')
if [ "$RESTAURANT_ID" = "null" ] || [ -z "$RESTAURANT_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Не удалось извлечь restaurant id: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 11: POST /api/v1/restaurants (невалидный body) -> 400... "
call_api "POST" "/api/v1/restaurants" "{}"
assert_status "400"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 12: GET /api/v1/restaurants/{id} -> 200... "
call_api "GET" "/api/v1/restaurants/$RESTAURANT_ID"
assert_status "200"
GOT_RESTAURANT_NAME=$(echo "$API_BODY" | jq -r '.name')
if [ "$GOT_RESTAURANT_NAME" != "$RESTAURANT_NAME" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидалось имя: $RESTAURANT_NAME"
    echo "  Получено: $GOT_RESTAURANT_NAME"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 13: PUT /api/v1/restaurants/{id} -> 200... "
call_api "PUT" "/api/v1/restaurants/$RESTAURANT_ID" "$RESTAURANT_UPDATE_PAYLOAD"
assert_status "200"
UPDATED_RESTAURANT_NAME=$(echo "$API_BODY" | jq -r '.name')
UPDATED_RESTAURANT_ADDRESS=$(echo "$API_BODY" | jq -r '.address')
if [ "$UPDATED_RESTAURANT_NAME" != "${RESTAURANT_NAME}-Updated" ] || [ "$UPDATED_RESTAURANT_ADDRESS" != "ул. Лермонтова, д. 5" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Не применилось обновление ресторана"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 14: PUT /api/v1/restaurants/{id} (невалидный body) -> 400... "
call_api "PUT" "/api/v1/restaurants/$RESTAURANT_ID" "{}"
assert_status "400"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 15: GET /api/v1/restaurants/999999 -> 404... "
call_api "GET" "/api/v1/restaurants/999999"
assert_status "404"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 16: POST /api/v1/restaurants (второй ресторан) -> 201... "
call_api "POST" "/api/v1/restaurants" "$RESTAURANT2_CREATE_PAYLOAD"
assert_status "201"
RESTAURANT2_ID=$(echo "$API_BODY" | jq -r '.id')
if [ "$RESTAURANT2_ID" = "null" ] || [ -z "$RESTAURANT2_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Не удалось извлечь restaurant2 id: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

# ==========================================
# DISHES (привязаны к ресторанам в ЛР-4)
# ==========================================

DISH_CREATE_PAYLOAD="{\"name\":\"${DISH_NAME}\",\"description\":\"CI test dish\",\"price\":499.0,\"isAvailable\":true}"
DISH2_CREATE_PAYLOAD="{\"name\":\"${DISH2_NAME}\",\"description\":\"CI test dish 2\",\"price\":299.0,\"isAvailable\":true}"
DISH3_CREATE_PAYLOAD="{\"name\":\"${DISH3_NAME}\",\"description\":\"Dish in restaurant 2\",\"price\":199.0,\"isAvailable\":true}"
DISH_UPDATE_PAYLOAD="{\"name\":\"${DISH_NAME}-Updated\",\"description\":\"Updated CI dish\",\"price\":799.0,\"isAvailable\":false}"

echo -n "Тест 17: POST /api/v1/restaurants/{id}/dishes -> 201... "
call_api "POST" "/api/v1/restaurants/$RESTAURANT_ID/dishes" "$DISH_CREATE_PAYLOAD"
assert_status "201"
assert_json_field_exists "id"
assert_json_field_exists "name"
assert_json_field_exists "description"
assert_json_field_exists "price"
assert_json_field_exists "isAvailable"
assert_json_field_exists "restaurantId"
DISH_ID=$(echo "$API_BODY" | jq -r '.id')
GOT_RESTAURANT_ID=$(echo "$API_BODY" | jq -r '.restaurantId')
if [ "$DISH_ID" = "null" ] || [ -z "$DISH_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Не удалось извлечь dish id: $API_BODY"
    exit 1
fi
if [ "$GOT_RESTAURANT_ID" != "$RESTAURANT_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался restaurantId: $RESTAURANT_ID, получен: $GOT_RESTAURANT_ID"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 18: POST /api/v1/restaurants/{id}/dishes (невалидный body) -> 400... "
call_api "POST" "/api/v1/restaurants/$RESTAURANT_ID/dishes" "{}"
assert_status "400"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 19: POST /api/v1/restaurants/999999/dishes (несуществующий ресторан) -> 404... "
call_api "POST" "/api/v1/restaurants/999999/dishes" "$DISH_CREATE_PAYLOAD"
assert_status "404"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 20: POST /api/v1/restaurants/{id}/dishes (второе блюдо) -> 201... "
call_api "POST" "/api/v1/restaurants/$RESTAURANT_ID/dishes" "$DISH2_CREATE_PAYLOAD"
assert_status "201"
DISH2_ID=$(echo "$API_BODY" | jq -r '.id')
if [ "$DISH2_ID" = "null" ] || [ -z "$DISH2_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Не удалось извлечь dish2 id: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 21: GET /api/v1/dishes -> 200 и массив... "
call_api "GET" "/api/v1/dishes"
assert_status "200"
if ! echo "$API_BODY" | jq -e 'type == "array"' > /dev/null; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался массив JSON"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 22: GET /api/v1/dishes?namePart=... -> 200 и содержит блюдо... "
call_api "GET" "/api/v1/dishes?namePart=$DISH_NAME"
assert_status "200"
FOUND_COUNT=$(echo "$API_BODY" | jq "[.[] | select(.id == $DISH_ID)] | length")
if [ "$FOUND_COUNT" -lt 1 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  В ответе нет блюда с id=$DISH_ID"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 23: GET /api/v1/dishes/{id} -> 200 и проверка restaurantId... "
call_api "GET" "/api/v1/dishes/$DISH_ID"
assert_status "200"
GOT_DISH_NAME=$(echo "$API_BODY" | jq -r '.name')
GOT_DISH_RESTAURANT_ID=$(echo "$API_BODY" | jq -r '.restaurantId')
if [ "$GOT_DISH_NAME" != "$DISH_NAME" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидалось имя блюда: $DISH_NAME"
    echo "  Получено: $GOT_DISH_NAME"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
if [ "$GOT_DISH_RESTAURANT_ID" != "$RESTAURANT_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался restaurantId: $RESTAURANT_ID, получен: $GOT_DISH_RESTAURANT_ID"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 24: PUT /api/v1/dishes/{id} -> 200... "
call_api "PUT" "/api/v1/dishes/$DISH_ID" "$DISH_UPDATE_PAYLOAD"
assert_status "200"
UPDATED_DISH_NAME=$(echo "$API_BODY" | jq -r '.name')
UPDATED_DISH_AVAILABLE=$(echo "$API_BODY" | jq -r '.isAvailable')
if [ "$UPDATED_DISH_NAME" != "${DISH_NAME}-Updated" ] || [ "$UPDATED_DISH_AVAILABLE" != "false" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Не применилось обновление блюда"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 25: PUT /api/v1/dishes/{id} (невалидный body) -> 400... "
call_api "PUT" "/api/v1/dishes/$DISH_ID" "{}"
assert_status "400"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 26: GET /api/v1/restaurants/{id}/dishes -> 200 меню ресторана... "
call_api "GET" "/api/v1/restaurants/$RESTAURANT_ID/dishes"
assert_status "200"
if ! echo "$API_BODY" | jq -e 'type == "array"' > /dev/null; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался массив JSON"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
MENU_COUNT=$(echo "$API_BODY" | jq 'length')
if [ "$MENU_COUNT" -lt 2 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидалось минимум 2 блюда в меню ресторана, получено: $MENU_COUNT"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 27: GET /api/v1/restaurants/999999/dishes (несуществующий ресторан) -> 404... "
call_api "GET" "/api/v1/restaurants/999999/dishes"
assert_status "404"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 28: POST блюдо во второй ресторан -> 201... "
call_api "POST" "/api/v1/restaurants/$RESTAURANT2_ID/dishes" "$DISH3_CREATE_PAYLOAD"
assert_status "201"
DISH3_ID=$(echo "$API_BODY" | jq -r '.id')
GOT_DISH3_RESTAURANT_ID=$(echo "$API_BODY" | jq -r '.restaurantId')
if [ "$GOT_DISH3_RESTAURANT_ID" != "$RESTAURANT2_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Блюдо должно быть привязано к ресторану $RESTAURANT2_ID, а привязано к $GOT_DISH3_RESTAURANT_ID"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 29: Меню первого ресторана не содержит блюдо второго... "
call_api "GET" "/api/v1/restaurants/$RESTAURANT_ID/dishes"
assert_status "200"
DISH3_IN_MENU=$(echo "$API_BODY" | jq "[.[] | select(.id == $DISH3_ID)] | length")
if [ "$DISH3_IN_MENU" -ne 0 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Блюдо из ресторана 2 (id=$DISH3_ID) найдено в меню ресторана 1"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 30: GET /api/v1/dishes/{id} после delete -> 404... "
call_api "GET" "/api/v1/dishes/999999"
assert_status "404"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

# ==========================================
# ORDERS
# ==========================================

ORDER_CREATE_PAYLOAD="{\"userId\":${USER_ID},\"dishIds\":[${DISH_ID},${DISH2_ID}]}"

echo -n "Тест 31: POST /api/v1/orders -> 201... "
call_api "POST" "/api/v1/orders" "$ORDER_CREATE_PAYLOAD"
assert_status "201"
assert_json_field_exists "id"
assert_json_field_exists "userId"
assert_json_field_exists "status"
assert_json_field_exists "createdAt"
assert_json_field_exists "dishes"
ORDER_ID=$(echo "$API_BODY" | jq -r '.id')
ORDER_STATUS=$(echo "$API_BODY" | jq -r '.status')
ORDER_USER_ID=$(echo "$API_BODY" | jq -r '.userId')
ORDER_DISHES_COUNT=$(echo "$API_BODY" | jq '.dishes | length')
if [ "$ORDER_ID" = "null" ] || [ -z "$ORDER_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Не удалось извлечь order id: $API_BODY"
    exit 1
fi
if [ "$ORDER_STATUS" != "PENDING" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался статус PENDING, получен: $ORDER_STATUS"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
if [ "$ORDER_USER_ID" != "$USER_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался userId: $USER_ID, получен: $ORDER_USER_ID"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
if [ "$ORDER_DISHES_COUNT" -ne 2 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидалось 2 блюда в заказе, получено: $ORDER_DISHES_COUNT"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 32: POST /api/v1/orders (невалидный body) -> 400... "
call_api "POST" "/api/v1/orders" "{}"
assert_status "400"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 33: POST /api/v1/orders (несуществующий userId) -> 400... "
call_api "POST" "/api/v1/orders" "{\"userId\":999999,\"dishIds\":[${DISH_ID}]}"
assert_status "400"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 34: POST /api/v1/orders (несуществующий dishId) -> 400... "
call_api "POST" "/api/v1/orders" "{\"userId\":${USER_ID},\"dishIds\":[999999]}"
assert_status "400"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 35: POST /api/v1/orders (пустой dishIds) -> 400... "
call_api "POST" "/api/v1/orders" "{\"userId\":${USER_ID},\"dishIds\":[]}"
assert_status "400"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 36: GET /api/v1/orders/{id} -> 200... "
call_api "GET" "/api/v1/orders/$ORDER_ID"
assert_status "200"
assert_json_field_exists "id"
assert_json_field_exists "userId"
assert_json_field_exists "status"
assert_json_field_exists "createdAt"
assert_json_field_exists "dishes"
GOT_ORDER_DISHES_COUNT=$(echo "$API_BODY" | jq '.dishes | length')
if [ "$GOT_ORDER_DISHES_COUNT" -ne 2 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидалось 2 блюда в заказе, получено: $GOT_ORDER_DISHES_COUNT"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
# Проверяем, что блюда — полные объекты (есть id, name, price)
FIRST_DISH_HAS_NAME=$(echo "$API_BODY" | jq -r '.dishes[0].name')
if [ "$FIRST_DISH_HAS_NAME" = "null" ] || [ -z "$FIRST_DISH_HAS_NAME" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Блюда в заказе должны быть полными объектами (с name, price и т.д.)"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 37: GET /api/v1/orders -> 200 и массив... "
call_api "GET" "/api/v1/orders"
assert_status "200"
if ! echo "$API_BODY" | jq -e 'type == "array"' > /dev/null; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался массив JSON"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 38: GET /api/v1/orders?userId={id} -> 200 и содержит заказ... "
call_api "GET" "/api/v1/orders?userId=$USER_ID"
assert_status "200"
FOUND_ORDER_COUNT=$(echo "$API_BODY" | jq "[.[] | select(.id == $ORDER_ID)] | length")
if [ "$FOUND_ORDER_COUNT" -lt 1 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  В ответе нет заказа с id=$ORDER_ID при фильтре userId=$USER_ID"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 39: GET /api/v1/orders?status=PENDING -> 200 и содержит заказ... "
call_api "GET" "/api/v1/orders?status=PENDING"
assert_status "200"
FOUND_ORDER_COUNT=$(echo "$API_BODY" | jq "[.[] | select(.id == $ORDER_ID)] | length")
if [ "$FOUND_ORDER_COUNT" -lt 1 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  В ответе нет заказа с id=$ORDER_ID при фильтре status=PENDING"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 40: PATCH /api/v1/orders/{id}/status -> CONFIRMED -> 200... "
call_api "PATCH" "/api/v1/orders/$ORDER_ID/status" "{\"status\":\"CONFIRMED\"}"
assert_status "200"
PATCHED_STATUS=$(echo "$API_BODY" | jq -r '.status')
if [ "$PATCHED_STATUS" != "CONFIRMED" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался статус CONFIRMED, получен: $PATCHED_STATUS"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 41: PATCH /api/v1/orders/{id}/status -> DELIVERED -> 200... "
call_api "PATCH" "/api/v1/orders/$ORDER_ID/status" "{\"status\":\"DELIVERED\"}"
assert_status "200"
PATCHED_STATUS=$(echo "$API_BODY" | jq -r '.status')
if [ "$PATCHED_STATUS" != "DELIVERED" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался статус DELIVERED, получен: $PATCHED_STATUS"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 42: PATCH /api/v1/orders/{id}/status (невалидный переход) -> 400... "
call_api "PATCH" "/api/v1/orders/$ORDER_ID/status" "{\"status\":\"PENDING\"}"
assert_status "400"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 43: GET /api/v1/orders/999999 -> 404... "
call_api "GET" "/api/v1/orders/999999"
assert_status "404"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 44: GET /api/v1/orders?userId={id}&status=DELIVERED -> содержит заказ... "
call_api "GET" "/api/v1/orders?userId=${USER_ID}&status=DELIVERED"
assert_status "200"
FOUND_ORDER_COUNT=$(echo "$API_BODY" | jq "[.[] | select(.id == $ORDER_ID)] | length")
if [ "$FOUND_ORDER_COUNT" -lt 1 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  В ответе нет заказа при комбинированном фильтре userId+status"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

# ==========================================
# CLEANUP (удаление в обратном порядке)
# ==========================================

echo -n "Тест 45: DELETE /api/v1/dishes/{id} (блюдо 3) -> 204... "
call_api "DELETE" "/api/v1/dishes/$DISH3_ID"
assert_status "204"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 46: GET /api/v1/dishes/{id} после delete -> 404... "
call_api "GET" "/api/v1/dishes/$DISH3_ID"
assert_status "404"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 47: DELETE /api/v1/dishes/{id} (блюдо 2) -> 204... "
call_api "DELETE" "/api/v1/dishes/$DISH2_ID"
assert_status "204"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 48: DELETE /api/v1/dishes/{id} (блюдо 1) -> 204... "
call_api "DELETE" "/api/v1/dishes/$DISH_ID"
assert_status "204"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 49: DELETE /api/v1/restaurants/{id} (ресторан 2) -> 204... "
call_api "DELETE" "/api/v1/restaurants/$RESTAURANT2_ID"
assert_status "204"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 50: GET /api/v1/restaurants/{id} после delete -> 404... "
call_api "GET" "/api/v1/restaurants/$RESTAURANT2_ID"
assert_status "404"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 51: DELETE /api/v1/restaurants/{id} (ресторан 1) -> 204... "
call_api "DELETE" "/api/v1/restaurants/$RESTAURANT_ID"
assert_status "204"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 52: DELETE /api/v1/users/{id} -> 204... "
call_api "DELETE" "/api/v1/users/$USER_ID"
assert_status "204"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 53: GET /api/v1/users/{id} после delete -> 404... "
call_api "GET" "/api/v1/users/$USER_ID"
assert_status "404"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo "---------------------------------------------------"
echo -e "${GREEN}Все 53 теста успешно пройдены!${NC}"
exit 0
