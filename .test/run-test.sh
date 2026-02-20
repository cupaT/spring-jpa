#!/bin/bash

BASE_URL="${BASE_URL:-http://localhost:8080}"
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
echo "Запуск тестов API по спецификации lab/3/spec.yaml..."
echo "---------------------------------------------------"

SUFFIX="$(date +%s)-$$"
USER_EMAIL="ci-user-${SUFFIX}@example.com"
DISH_NAME="CI-Dish-${SUFFIX}"

USER_CREATE_PAYLOAD="{\"email\":\"${USER_EMAIL}\",\"firstName\":\"Ivan\",\"lastName\":\"Petrov\",\"isActive\":true}"
USER_UPDATE_PAYLOAD="{\"email\":\"${USER_EMAIL}\",\"firstName\":\"IvanUpdated\",\"lastName\":\"PetrovUpdated\",\"isActive\":false}"

DISH_CREATE_PAYLOAD="{\"name\":\"${DISH_NAME}\",\"description\":\"CI test dish\",\"price\":499.0,\"isAvailable\":true}"
DISH_UPDATE_PAYLOAD="{\"name\":\"${DISH_NAME}-Updated\",\"description\":\"Updated CI dish\",\"price\":799.0,\"isAvailable\":false}"

# ==========================================
# USERS
# ==========================================

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

echo -n "Тест 8: DELETE /api/v1/users/{id} -> 204... "
call_api "DELETE" "/api/v1/users/$USER_ID"
assert_status "204"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 9: GET /api/v1/users/{id} после delete -> 404... "
call_api "GET" "/api/v1/users/$USER_ID"
assert_status "404"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

# ==========================================
# DISHES
# ==========================================

echo -n "Тест 10: GET /api/v1/dishes -> 200 и массив... "
call_api "GET" "/api/v1/dishes"
assert_status "200"
if ! echo "$API_BODY" | jq -e 'type == "array"' > /dev/null; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался массив JSON"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 11: POST /api/v1/dishes (новое) -> 201... "
call_api "POST" "/api/v1/dishes" "$DISH_CREATE_PAYLOAD"
assert_status "201"
assert_json_field_exists "id"
assert_json_field_exists "name"
assert_json_field_exists "description"
assert_json_field_exists "price"
assert_json_field_exists "isAvailable"
DISH_ID=$(echo "$API_BODY" | jq -r '.id')
if [ "$DISH_ID" = "null" ] || [ -z "$DISH_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Не удалось извлечь dish id: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 12: POST /api/v1/dishes (существующее) -> 200... "
call_api "POST" "/api/v1/dishes" "$DISH_CREATE_PAYLOAD"
assert_status "200"
POST_DISH_ID=$(echo "$API_BODY" | jq -r '.id')
if [ "$POST_DISH_ID" != "$DISH_ID" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидался тот же id: $DISH_ID, получен: $POST_DISH_ID"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 13: POST /api/v1/dishes (невалидный body) -> 400... "
call_api "POST" "/api/v1/dishes" "{}"
assert_status "400"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 14: GET /api/v1/dishes/{id} -> 200... "
call_api "GET" "/api/v1/dishes/$DISH_ID"
assert_status "200"
GOT_DISH_NAME=$(echo "$API_BODY" | jq -r '.name')
if [ "$GOT_DISH_NAME" != "$DISH_NAME" ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  Ожидалось имя блюда: $DISH_NAME"
    echo "  Получено: $GOT_DISH_NAME"
    echo "  Тело ответа: $API_BODY"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

echo -n "Тест 15: GET /api/v1/dishes?namePart=... -> 200 и содержит блюдо... "
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

echo -n "Тест 16: PUT /api/v1/dishes/{id} -> 200... "
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

echo -n "Тест 17: PUT /api/v1/dishes/{id} (невалидный body) -> 400... "
call_api "PUT" "/api/v1/dishes/$DISH_ID" "{}"
assert_status "400"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 18: DELETE /api/v1/dishes/{id} -> 204... "
call_api "DELETE" "/api/v1/dishes/$DISH_ID"
assert_status "204"
echo -e "${GREEN}OK${NC}"

echo -n "Тест 19: GET /api/v1/dishes/{id} после delete -> 404... "
call_api "GET" "/api/v1/dishes/$DISH_ID"
assert_status "404"
assert_json_field_exists "status"
assert_json_field_exists "error"
assert_json_field_exists "message"
echo -e "${GREEN}OK${NC}"

echo "---------------------------------------------------"
echo -e "${GREEN}Все тесты успешно пройдены!${NC}"
exit 0
