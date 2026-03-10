# Лабораторная работа №4

## Связи в модели данных. PostgreSQL и миграции с Flyway

---

## Цель работы

Расширить проект доставки еды из ЛР-3: добавить сущности `Restaurant` и `Order`, реализовать связи между сущностями (
`@OneToMany`, `@ManyToMany`), перейти с H2 на PostgreSQL, внедрить Flyway для управления схемой БД и оптимизировать
загрузку связанных данных через `@EntityGraph`.

---

## Что нужно сдать

Ссылку на PR в ваш репозиторий (шаблон у вас есть).

---

## Теоретический блок

### 1) PostgreSQL через docker-compose

В ЛР-3 использовалась H2. Теперь переходим на PostgreSQL, более подходящей для продакшена СУБД.

Ниже представлен примерный `docker-compose.yaml` для PostgreSQL.  
`Да`, для дальнейшей работы вам понадобится `Docker`. Рассмотрим процесс установки для различных ОС.

#### Linux (Ubuntu / Fedora)

Для Debian-like дистрибутивов выполните следующие шаги:
```bash
# Удалить старые версии (если были)
sudo apt remove docker docker-engine docker.io containerd runc

# Установить зависимости
sudo apt update
sudo apt install ca-certificates curl gnupg

# Добавить GPG-ключ Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Добавить репозиторий
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Установить Docker Engine + Docker Compose
sudo apt update
sudo apt install docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

Для RedHat-based систем аналогичное действие будет выглядеть следующим образом^
```bash
sudo dnf install dnf-plugins-core
sudo dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo
sudo dnf install docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

После установки на любой из дистрибутивов нужно выполнить следующие шаги:
```bash
# Запустить Docker
sudo systemctl start docker
sudo systemctl enable docker

# Добавить текущего пользователя в группу docker (чтобы не писать sudo)
sudo usermod -aG docker $USER

# Перелогиниться или выполнить
newgrp docker

# Проверить
docker --version
docker compose version
```

---

#### Windows

Вариант 1: Docker Desktop (рекомендуется)

1. Для начала установить `WSL 2`. Для этого нужно открыть PowerShell от имени администратора и выполнить команду ниже
```shell
wsl --install

# Ну или если первый вариант не работает
wsl.exe --install
```
2. Скачать установщик: https://www.docker.com/products/docker-desktop/
3. Запустить `.exe`, следовать инструкциям.
4. При установке выбрать **WSL 2 backend** (не Hyper-V).
5. После установки перезагрузить компьютер.
6. Запустить Docker Desktop, дождаться зелёного индикатора.

Вариант 2: Docker Engine через WSL 2

Если не хотите Docker Desktop:
1. Установить WSL 2 (см выше).
2. Установить Ubuntu из Microsoft Store.
3. Внутри Ubuntu выполнить команды из секции **Linux (Ubuntu)** выше.

После установки сделайте проверку работоспособности Docker (PowerShell или WSL-терминал).
```bash
docker --version
docker compose version
docker run hello-world
```

Далее создадим `docker-compose.yaml` в корне проекта, чтобы работать с PostgreSQL без непосредственной установки на
устройство.

**`docker-compose.yaml`:**

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: postgres
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

Далее проверим корректность файла, выполнив команды:

```bash
# В корне проекта
docker compose up -d

# Проверить, что контейнер запущен
docker compose ps

# Подключиться к БД (опционально)
docker compose exec postgres psql -U postgres

# Остановить
docker compose down
```

Затем необходимо в `application.yaml` указать `url`, `username` и `password` для субд.

**`application.yaml`:**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
```

Важно: `ddl-auto` теперь `validate`, а не `update` — схемой управляет Flyway.

#### Смена порта приложения

По умолчанию Spring Boot запускается на порту `8080`. Если он занят (например, другим сервисом), порт можно изменить.

**Напрямую в `application.yaml`:**

```yaml
server:
  port: 8081
```

**Через `.env` файл:**

Чтобы не хардкодить порт, можно вынести его в переменную окружения. Создайте файл `.env` в корне проекта:
```
SERVER_PORT=8081
```

А в `application.yaml` сослаться на неё:
```yaml
server:
  port: ${SERVER_PORT:8080}
```

Здесь `8080` — значение по умолчанию, если переменная не задана.

> Этот же подход (`${ПЕРЕМЕННАЯ:значение_по_умолчанию}`) работает для любых настроек — datasource, flyway и т.д.

**Как подтянуть переменные из `.env` при запуске:**

Spring Boot **не** читает `.env` файл автоматически. Нужно загрузить переменные самостоятельно.

Через Maven-плагин:
```bash
# Linux / macOS
export $(cat .env | xargs) && ./mvnw spring-boot:run

# Windows (PowerShell)
Get-Content .env | ForEach-Object { $k, $v = $_ -split '=', 2; [System.Environment]::SetEnvironmentVariable($k, $v) }; ./mvnw spring-boot:run
```

Через скомпилированный JAR:
```bash
# Сначала собираем
./mvnw clean package -DskipTests

# Запуск с загрузкой переменных из .env
export $(cat .env | xargs) && java -jar target/*.jar

# Или можно передать порт напрямую без .env файла
java -jar target/*.jar --server.port=8081
```

> **PS**: В процессе выполнения этой лабораторной работы вы установите WSL. По сути у вас теперь есть Linux-терминал в вашей Windows, и многие команды вы можете выполнять в нем

**Зависимость в `pom.xml`:**

```xml

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

### 2) Flyway: управление схемой БД

Flyway берет готовые SQL-файлы и накатывает их на базу данных в строгом порядке, записывая историю в специальную таблицу (flyway_schema_history).

#### Шаг 1. Настройка проекта

Далее внесем изменения в `pom.xml` для корректной работы с PostgreSQL и Flyway.
```xml
<project>
    <dependencies>

        <!-- ... -->

        <!-- Ядро Flyway -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>

        <!-- Модуль Flyway для PostgreSQL (обязателен для новых версий!) -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- Драйвер PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            
            <!-- ... -->

            <plugin>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-maven-plugin</artifactId>
                <!-- Версия должна совпадать с версией ядра Flyway в Spring Boot 4 (обычно 12.x) -->
                <version>12.0.2</version>
                <configuration>
                    <url>jdbc:postgresql://localhost:5432/flyway_demo</url>
                    <user>postgres</user>
                    <password>password</password>
                    <!-- Указываем, где лежат скрипты -->
                    <locations>
                        <location>filesystem:src/main/resources/db/migration</location>
                    </locations>
                </configuration>
                <dependencies>
                    <!-- Плагину нужны те же зависимости, что и основному проекту -->
                    <dependency>
                        <groupId>org.postgresql</groupId>
                        <artifactId>postgresql</artifactId>
                        <version>42.7.2</version>
                    </dependency>
                    <!-- И модуль для PostgreSQL для Flyway 10+ -->
                    <dependency>
                        <groupId>org.flywaydb</groupId>
                        <artifactId>flyway-database-postgresql</artifactId>
                        <version>12.0.2</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

Затем добавим изменения в `application.yaml`
```yaml
spring:
  
  #  ...

  flyway:
    # Flyway включен по умолчанию, но можно указать явно
    enabled: true
    # Если БД не пустая, но таблицы flyway_schema_history нет, 
    # baseline-on-migrate позволит начать версионирование
    baseline-on-migrate: true
    # Можно явным образом указать путь до директории с миграциями
    locations:
      - classpath:/db/migration
```

#### Команды плагина `FlyWay`

* `./mvnw flyway:info` (Информация)
* `./mvnw flyway:clean` (Очистка)
* `./mvnw flyway:migrate` (Миграция)
* `./mvnw flyway:validate` (Проверка миграций)
* `./mvnw flyway:baseline` (Создание базы данных)
* `./mvnw flyway:undo` (Откат). 💰 ПЛАТНАЯ ФУНКЦИЯ

#### Как передавать параметры подключения субд без pom.xml?
Если не хочется жестко прописывать настройки соединения внутри кода, то их тоже можно указать при запуске команды.

> Вообще указывать данные для подключения в исходниках `ОЧЕНЬ ПЛОХАЯ ПРАКТИКА`. Вы так можете вынести важные данные
> на общее обозрение и потерять доступ к ним.

```bash
./mvnw flyway:info \
  -Dflyway.url=jdbc:postgresql://localhost:5432/flyway_demo \
  -Dflyway.user=postgres \
  -Dflyway.password=supersecret
```

#### Шаг 2. Создание скрипта миграции

Для начала нужно создать скрипт миграции.  
Формат: `V<Версия>__<Описание>.sql` (Обратите внимание: `ДВА` подчеркивания после версии).

Создадим файл: `src/main/resources/db/migration/V1__create_users_table.sql`
```sql
-- V1__create_users.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### Шаг 3. Создание сущности пользователя

Теперь опишем эту таблицу в коде. В Kotlin для JPA-сущностей лучше использовать обычные классы (не data class), так как data class генерирует методы equals/hashCode/toString, которые могут вызывать проблемы с ленивой загрузкой (Lazy Loading) в Hibernate.

Создадим файл User.kt:
```kotlin
@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    
    @Column(name = "username", nullable = false, unique = true)
    var username: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)
```

#### Шаг 4. Изменение сущности и создание новой миграции

Представьте, что через неделю бизнес попросил добавить пользователям `email`.
> Золотое правило Flyway: Никогда не меняй уже примененные миграции! (Flyway считает их контрольные суммы, и если ты
> изменишь V1, приложение не запустится с ошибкой checksum mismatch).

Вместо этого мы создаем новую миграцию:
Файл: `src/main/resources/db/migration/V2__add_email_to_users.sql`
```sql
ALTER TABLE users 
ADD COLUMN email VARCHAR(255);

-- Если нужно, можно сразу добавить уникальный индекс
ALTER TABLE users 
ADD CONSTRAINT uk_users_email UNIQUE (email);
```

И обновляем нашу сущность `User.kt`, добавляя новое поле:
```kotlin
@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    
    @Column(name = "username", nullable = false, unique = true)
    var username: String,
    
    @Column(name = "email", unique = true)
    var email: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)
```

#### Шаг 6. Запуск

При старте Spring Boot приложения (например, через команду mvn spring-boot:run или из IDE) произойдет следующее:

* Flyway подключится к БД.
* Проверит наличие служебной таблицы `flyway_schema_history`. Если её нет — создаст.
* Посмотрит, какие миграции уже применены.
* Накатит `V1__create_users_table.sql`.
* Накатит `V2__add_email_to_users.sql`.
* После этого запустится Hibernate, который благодаря `ddl-auto: validate` проверит, что класс `User` полностью соответствует таблице users в БД.

---

## Практическое задание

### 1) Поднимите PostgreSQL

1. Создайте `docker-compose.yaml` с PostgreSQL.
2. Настройте `application.yaml` с подключением к PostgreSQL.
3. Убедитесь, что приложение запускается и подключается к БД.

### 2) Создайте Flyway-миграции

Создайте SQL-файлы в `resources/db/migration/`:

1. `V1__create_users.sql` — таблица `users`.
2. `V2__create_restaurants.sql` — таблица `restaurants`.
3. `V3__create_dishes.sql` — таблица `dishes` с FK `restaurant_id`.
4. `V4__create_orders.sql` — таблица `orders` с FK `user_id`.
5. `V5__create_order_dishes.sql` — промежуточная таблица `order_dishes`.

Установите `ddl-auto=validate`.

### 3) Добавьте новые сущности

#### `Restaurant`

1. `id: Long`
2. `name: String`
3. `address: String`
4. Связь `@OneToMany` → `Dish`

#### `Order`

1. `id: Long`
2. `status: OrderStatus` (enum: `PENDING`, `CONFIRMED`, `DELIVERED`, `CANCELLED`)
3. `createdAt: LocalDateTime`
4. Связь `@ManyToOne` → `User`
5. Связь `@ManyToMany` → `Dish`

#### Обновление `Dish`

1. Добавьте связь `@ManyToOne` → `Restaurant` (поле `restaurant_id`).

### 4) Реализуйте эндпоинты строго по `spec.yaml`

Реализуйте все эндпоинты, описанные в спецификации:

1. CRUD для `Restaurant`.
2. Получение меню ресторана (`GET /api/v1/restaurants/{id}/dishes`).
3. Добавление блюда в ресторан (`POST /api/v1/restaurants/{restaurantId}/dishes`).
4. Создание и чтение заказов (`POST /api/v1/orders`, `GET /api/v1/orders/{id}`).
5. Список заказов с фильтрами (`GET /api/v1/orders`).
6. Обновление статуса заказа (`PATCH /api/v1/orders/{id}/status`).

### 5) Используйте @EntityGraph

1. При загрузке заказа по id — подгружайте блюда и пользователя одним запросом.
2. При загрузке меню ресторана — подгружайте блюда без N+1.
3. Убедитесь через `show-sql=true`, что лишних запросов нет.

### 6) Архитектурные требования

1. Сохраните слоистую / Clean Architecture из ЛР-3.
2. Entity ≠ DTO: явный маппинг между слоями.

---

## Критерии оценки (максимум 20 баллов)

| Категория                   | Критерий                                                   | Баллы  |
|:----------------------------|:-----------------------------------------------------------|:------:|
| Штраф                       | Не проходят автотесты                                      |  -10   |
| PostgreSQL + docker-compose | БД поднимается, приложение подключается                    |   3    |
| Flyway                      | Миграции создают полную схему, `ddl-auto=validate`         |   3    |
| Связи: OneToMany            | `Restaurant → Dish` реализован корректно                   |   2    |
| Связи: ManyToMany           | `Order ↔ Dish` через `@JoinTable` или промежуточную Entity |   2    |
| CRUD: Restaurant            | Полный CRUD работает через API                             |   3    |
| CRUD: Order                 | Создание и чтение заказов с блюдами                        |   3    |
| @EntityGraph / JOIN FETCH   | N+1 отсутствует при чтении связанных данных                |   2    |
| Качество решения            | Архитектура, маппинг Entity↔DTO, чистота кода              |   2    |
| **Итого**                   |                                                            | **20** |

---

## Мини-чеклист перед сдачей

1. `docker-compose up` поднимает PostgreSQL, приложение подключается.
2. Миграции Flyway создают все таблицы при первом запуске.
3. CRUD для Restaurant и Dish работает через HTTP.
4. Создание заказа с указанием блюд работает.
5. `GET /api/v1/orders/{id}` возвращает заказ с блюдами и информацией о пользователе.
6. В логах (`show-sql=true`) нет лишних запросов при чтении связанных данных.
7. `ddl-auto=validate` — Hibernate не меняет схему, только проверяет.

---

## Что почитать

1. [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/)
2. [Flyway Documentation](https://documentation.red-gate.com/flyway)
3. [Baeldung — JPA One-to-Many](https://www.baeldung.com/hibernate-one-to-many)
4. [Baeldung — JPA Many-to-Many](https://www.baeldung.com/jpa-many-to-many)
5. [Baeldung — JPA Entity Graph](https://www.baeldung.com/jpa-entity-graph)
6. [PostgreSQL + Docker](https://hub.docker.com/_/postgres)
7. [Spring Boot Flyway Guide](https://www.baeldung.com/database-migrations-with-flyway)
