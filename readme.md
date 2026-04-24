# Лабораторная работа №8

## Документация API, контейнеризация и CI/CD

---

## Цель работы

Задокументировать REST API через SpringDoc, упаковать приложение в Docker-образ, поднять весь стек через docker-compose и настроить автоматическую доставку образа в реестр через GitHub Actions.

---

## Что нужно сдать

Ссылку на PR в ваш репозиторий (шаблон у вас есть).

---

## Теоретический блок

### 1) Документирование API: SpringDoc OpenAPI

#### Зачем это нужно

Когда API растёт, его становится сложно изучать по коду. SpringDoc автоматически генерирует документацию из ваших контроллеров и публикует интерактивный Swagger UI — браузерный интерфейс, через который можно смотреть все эндпоинты и сразу отправлять запросы.

#### Зависимость

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.9</version>
</dependency>
```

После добавления зависимости Swagger UI доступен по адресу `http://localhost:8080/swagger-ui.html` — без каких-либо дополнительных настроек.

OpenAPI JSON/YAML схема: `http://localhost:8080/v3/api-docs`.

#### Настройка мета-информации (опционально)

```kotlin
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Food Delivery API")
                .version("1.0.0")
                .description("REST API сервиса доставки еды")
        )
}
```

#### Swagger UI и Spring Security

Если у вас настроена Spring Security (из ЛР-7), пути Swagger UI нужно явно разрешить в `SecurityFilterChain`:

```kotlin
.authorizeHttpRequests { auth ->
    auth
        .requestMatchers(
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
        ).permitAll()
        // ... остальные правила
}
```

#### Аннотирование контроллеров

Основные аннотации из пакета `io.swagger.v3.oas.annotations`:

- `@Tag(name = "...")` — группировка эндпоинтов в UI
- `@Operation(summary = "...")` — краткое описание метода
- `@ApiResponse(responseCode = "200", description = "...")` — описание кода ответа
- `@Parameter(description = "...")` — описание параметра запроса
- `@Schema(description = "...")` — описание поля DTO

Пример:

```kotlin
@RestController
@RequestMapping("/api/restaurants")
@Tag(name = "Restaurants", description = "Управление ресторанами")
class RestaurantController(private val restaurantService: RestaurantService) {

    @GetMapping("/{id}")
    @Operation(summary = "Получить ресторан по ID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ресторан найден"),
            ApiResponse(responseCode = "404", description = "Ресторан не найден")
        ]
    )
    fun getById(@PathVariable id: Long): ResponseEntity<RestaurantDto> =
        ResponseEntity.ok(restaurantService.getById(id))
}
```

Аннотировать все эндпоинты необязательно — SpringDoc подхватит их автоматически. Аннотации нужны там, где автоматически сгенерированное описание недостаточно понятно.

---

### 2) Dockerfile: упаковка приложения

#### Принцип работы

Docker упаковывает приложение и его окружение (JRE, конфиги) в образ — изолированный исполняемый пакет. Образ одинаково запускается на любой машине, где установлен Docker.

#### Multi-stage сборка

Наивный подход — скопировать уже собранный JAR в образ:

```dockerfile
FROM eclipse-temurin:21-jre
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Проблема: нужно сначала собрать JAR локально командой `mvn package`, и `target/` должен существовать до сборки образа.

Лучший вариант — **multi-stage build**: Maven запускается внутри контейнера, итоговый образ содержит только JRE и JAR:

```dockerfile
# Stage 1: сборка
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Финальный образ не содержит JDK, Maven и исходников — только JRE (~270 MB) и JAR. Это важно для безопасности и размера.

#### .dockerignore

Аналог `.gitignore` — указывает, что не нужно копировать в контекст сборки. Ускоряет сборку и исключает чувствительные файлы:

```
target/
.git/
.github/
.idea/
*.md
.env
```

> `target/` не нужен: в multi-stage сборке Maven работает внутри контейнера.

#### Проверка локально

```bash
docker build -t food-delivery:latest .
docker run -p 8080:8080 food-delivery:latest
```

---

### 3) Docker Compose: поднять весь стек

В ЛР-4 вы уже добавили docker-compose.yaml с сервисом `postgres`. Теперь добавьте к нему два новых сервиса: `app` (ваш REST API) и `pgadmin` (веб-интерфейс для базы данных).

#### Сети

Все сервисы в одном compose-файле по умолчанию оказываются в одной сети. Docker DNS резолвит имя сервиса в IP контейнера — поэтому в `SPRING_DATASOURCE_URL` нужно писать имя сервиса (`postgres`), а не `localhost`.

#### healthcheck + depends_on

Без `condition: service_healthy` приложение может стартовать раньше, чем postgres будет готов принимать соединения — Spring выбросит исключение при старте. Решение:

1. Добавить `healthcheck` на сервис `postgres` — он периодически проверяет готовность через `pg_isready`.
2. Добавить `depends_on` с `condition: service_healthy` на сервис `app` — compose дождётся зелёного статуса перед запуском приложения.

#### pgAdmin

Образ: `dpage/pgadmin4`. После запуска pgAdmin доступен в браузере. Для подключения к БД внутри pgAdmin в качестве хоста укажите имя сервиса postgres в compose-файле — Docker DNS сам разрешит его в нужный IP.

#### Переменные окружения: .env файл

Хранить логины и пароли прямо в docker-compose.yaml — плохая практика: они попадут в git. Docker Compose автоматически читает файл `.env` из той же директории и подставляет переменные через синтаксис `${VAR_NAME}`.

Пример `.env`:

```
POSTGRES_DB=delivery
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
PGADMIN_EMAIL=admin@admin.com
PGADMIN_PASSWORD=admin
```

В docker-compose.yaml переменные используются так:

```yaml
environment:
  POSTGRES_DB: ${POSTGRES_DB}
  POSTGRES_USER: ${POSTGRES_USER}
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
```

Файл `.env` **не должен попасть в git** — добавьте его в `.gitignore`. Вместо него коммитьте `.env.example` с теми же ключами, но пустыми значениями — это документация для других разработчиков.

#### Команды

```bash
docker compose up --build     # собрать образ и поднять стек
docker compose up -d           # в фоне
docker compose down            # остановить и удалить контейнеры
docker compose logs -f app     # смотреть логи приложения
```

---

### 4) GitHub Actions: публикация образа (CD)

До сих пор `ci.yaml` запускал тесты на каждый PR. Теперь добавим `cd.yaml`, который будет собирать образ и отправлять его в реестр при каждом push в основную ветку.

Разделение на два файла — стандартная практика:
- **CI** (Continuous Integration) — проверяем качество на PR, до мержа
- **CD** (Continuous Delivery) — доставляем артефакт после мержа в main

Вы можете выбрать **один** из двух реестров — DockerHub или GHCR.

---

#### Вариант A: DockerHub

**Подготовка:**
1. Зарегистрируйтесь на [hub.docker.com](https://hub.docker.com)
2. Account Settings → Security → **New Access Token** (разрешения: Read, Write, Delete). Скопируйте токен — он показывается только один раз.
3. Откройте ваш репозиторий на GitHub → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**. Добавьте два секрета:
   - `DOCKERHUB_USERNAME` — ваш логин на DockerHub
   - `DOCKERHUB_TOKEN` — токен из шага 2

Секреты зашифрованы и недоступны для чтения после сохранения. В логах Actions они автоматически маскируются (`***`).

```yaml
# .github/workflows/cd.yaml
name: CD

on:
  push:
    branches: [ main ]

jobs:
  build-and-push:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Log in to DockerHub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: ${{ secrets.DOCKERHUB_USERNAME }}/food-delivery:latest
```

Образ будет доступен по адресу `docker.io/<ваш-логин>/food-delivery:latest`.

---

#### Вариант B: GHCR (GitHub Container Registry)

GHCR встроен в GitHub и не требует отдельного аккаунта. Каждый GitHub Actions workflow автоматически получает временный `GITHUB_TOKEN` — именно он используется для аутентификации.

**Подготовка:** Никакой. Только добавьте `permissions` в файл workflow (см. ниже).

```yaml
# .github/workflows/cd.yaml
name: CD

on:
  push:
    branches: [ master ]

permissions:
  contents: read
  packages: write

jobs:
  build-and-push:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: ghcr.io/${{ github.repository_owner }}/food-delivery:latest
```

После успешного прогона образ появится в разделе **Packages** вашего GitHub-профиля.

---

### 5) ci.yaml: проверьте триггер

У вас уже есть `ci.yaml` с предыдущих работ. Убедитесь, что он запускается на pull request в основную ветку:

```yaml
on:
  pull_request:
    branches: [ master ]
```

Если стоит `push` или `workflow_dispatch` — замените или добавьте `pull_request`.

---

## Практическое задание

### 1) SpringDoc

- Добавьте зависимость в `pom.xml`
- Если настроена Spring Security — откройте пути Swagger UI в `SecurityFilterChain`
- Аннотируйте минимум **3 эндпоинта**: `@Operation`, `@ApiResponse`
- Убедитесь: `http://localhost:8080/swagger-ui.html` открывается и отображает ваш API

### 2) Dockerfile

- Создайте `Dockerfile` в корне проекта (multi-stage: builder + runtime)
- Создайте `.dockerignore`
- Убедитесь: `docker build -t food-delivery .` завершается без ошибок

### 3) docker-compose.yaml

- К существующему сервису `postgres` добавьте `app` и `pgadmin`
- Добавьте `healthcheck` для postgres и `depends_on: condition: service_healthy` для app
- Вынесите все переменные окружения в `.env`, добавьте `.env` в `.gitignore`, создайте `.env.example` с теми же ключами и пустыми значениями
- Убедитесь: `docker compose up --build` поднимает все три сервиса, приложение стартует и отвечает на запросы

### 4) cd.yaml

- Создайте `.github/workflows/cd.yaml`
- Выберите реестр: DockerHub или GHCR
- Добавьте нужные секреты в GitHub (для DockerHub)
- Сделайте push в основную ветку — workflow должен отработать, образ должен появиться в реестре

### 5) ci.yaml

- Проверьте триггер — должен быть `pull_request` на основную ветку
- Если нет — исправьте

---

## Критерии оценки (максимум 15 баллов)

| Категория              | Критерий                                                                     | Баллы  |
|:-----------------------|:-----------------------------------------------------------------------------|:------:|
| SpringDoc              | Зависимость добавлена, Swagger UI открывается, аннотации на 3+ эндпоинтах   |   3    |
| Dockerfile             | Multi-stage build, корректный ENTRYPOINT, `.dockerignore`                    |   4    |
| docker-compose         | Сервисы app + pgAdmin, переменные через `.env`, `.env.example` в репозитории |   3    |
| healthcheck + порядок  | `healthcheck` на postgres, `depends_on: condition: service_healthy` для app  |   3    |
| cd.yaml                | Собирает образ и пушит в реестр (DockerHub или GHCR)                         |   2    |
| **Итого**              |                                                                              | **15** |

Штраф: `ci.yaml` не имеет триггера на `pull_request` — **−2 балла**.

---

## Мини-чеклист перед сдачей

1. `docker build .` завершается без ошибок.
2. `docker compose up --build` поднимает postgres + app + pgadmin.
3. На сервисе `postgres` настроен `healthcheck`, сервис `app` стартует только после его прохождения (`depends_on: condition: service_healthy`).
4. Переменные окружения вынесены в `.env`, в репозитории есть `.env.example`, `.env` в `.gitignore`.
5. Swagger UI доступен по `/swagger-ui.html` и отображает эндпоинты.
6. cd.yaml сработал при последнем push в main — образ виден в реестре.
7. ci.yaml триггерится на `pull_request`.

---

## Что почитать

1. [SpringDoc OpenAPI](https://springdoc.org/)
2. [SpringDoc + Spring Security](https://springdoc.org/#spring-security-integration)
3. [Swagger Annotations](https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations)
4. [Dockerfile reference](https://docs.docker.com/reference/dockerfile/)
5. [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
6. [Docker Compose reference](https://docs.docker.com/compose/compose-file/)
7. [docker/login-action](https://github.com/docker/login-action)
8. [docker/build-push-action](https://github.com/docker/build-push-action)
9. [DockerHub access tokens](https://docs.docker.com/security/for-developers/access-tokens/)
10. [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
