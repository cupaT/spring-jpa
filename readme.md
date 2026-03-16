# Лабораторная работа №5

## Обработка ошибок. Валидация. Логгирование

---

## Цель работы

Добавить в проект доставки еды из ЛР-4: валидацию входных данных (`@Valid`), единую обработку ошибок через `@RestControllerAdvice` с иерархией кастомных исключений и структурированное логгирование ключевых событий.

---

## Что нужно сдать

Ссылку на PR в ваш репозиторий (шаблон у вас есть).

---

## Теоретический блок

### 1) Зачем нужна обработка ошибок

Сейчас в вашем проекте ошибки возвращаются как попало: Spring сам формирует ответ с трейсом, статусы непредсказуемы, клиент не знает, чего ожидать.

Пример того, что Spring вернёт по умолчанию при необработанном исключении:
```json
{
  "timestamp": "2025-03-07T12:00:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "trace": "java.lang.RuntimeException: Something went wrong\n\tat com.example...",
  "path": "/api/v1/restaurants"
}
```

Проблемы:
- Клиент видит внутренности сервера (`trace`) — это небезопасно.
- Формат меняется от ошибки к ошибке.
- Нет полезной информации о том, **что именно** пошло не так.

Цель — сделать так, чтобы API **всегда** возвращал предсказуемый формат ошибки с правильным HTTP-статусом.

---

### 2) Единый формат ответа об ошибке

Определим DTO для ошибок. Базовый класс — `ErrorResponse`, для ошибок валидации — наследник с деталями по полям:

```kotlin
open class ErrorResponse(
    val status: Int,
    val message: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

class ValidationErrorResponse(
    status: Int,
    message: String? = null,
    val errors: Map<String, String>,
    timestamp: LocalDateTime = LocalDateTime.now()
) : ErrorResponse(status, message, timestamp)
```

> `data class` нельзя наследовать от другого `data class`, поэтому используем обычные классы с `open`.

Пример ответа при ошибке валидации:
```json
{
  "status": 400,
  "message": "Ошибка валидации",
  "errors": {
    "name": "Название не может быть пустым",
    "price": "Цена должна быть больше 0"
  },
  "timestamp": "2025-03-07T12:00:00"
}
```

---

### 3) Иерархия кастомных исключений

У приложения должна быть собственная надстройка исключений над системными. Бизнес-логика не должна бросать голые Spring/JPA-исключения — она бросает свои, а `@ControllerAdvice` маппит их на HTTP-статусы.

Удобный подход — `sealed class`:

```kotlin
sealed class AppException(message: String) : RuntimeException(message)

class NotFoundException(message: String) : AppException(message)

class AlreadyExistsException(message: String) : AppException(message)

class InvalidOrderStateException(message: String) : AppException(message)
```

Преимущества `sealed class`:
- Компилятор Kotlin гарантирует, что `when`-выражение покрывает все варианты.
- Иерархия закрыта — нельзя случайно добавить наследника в другом модуле.
- Каждый тип исключения несёт **семантику**, а не просто сообщение.

Использование в сервисном слое:

```kotlin
@Service
class RestaurantService(
    private val restaurantRepository: RestaurantRepositoryPort
) {
    fun getById(id: Long): Restaurant {
        return restaurantRepository.findById(id)
            ?: throw NotFoundException("Ресторан с id=$id не найден")
    }

    fun create(command: CreateRestaurantCommand): Restaurant {
        if (restaurantRepository.existsByName(command.name)) {
            throw AlreadyExistsException("Ресторан '${command.name}' уже существует")
        }
        return restaurantRepository.save(command.toEntity())
    }
}
```

> Обратите внимание: сервис не знает про HTTP-статусы. Он бросает доменное исключение, а маппинг на `404`/`409` происходит в `@ControllerAdvice`.

---

### 4) Глобальный обработчик ошибок: @RestControllerAdvice

`@RestControllerAdvice` — это специальный бин Spring, который перехватывает исключения, выброшенные из контроллеров, и формирует ответ.

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {
    
    @ExceptionHandler(AppException::class)
    fun handleCommon(e: AppException): ResponseEntity<ErrorResponse> {        
        val status = when (e) {
            is NotFoundException -> status = HttpStatus.NOT_FOUND
            is AlreadyExistsException -> status = HttpStatus.CONFLICT
            is InvalidOrderStateException,
            is BadCredentialsException -> status = Http.BAD_REQUEST
            // ...
        }
        
        return ResponseEntity
            .status(status)
            .body(ErrorResponse(status.value(), e.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ValidationErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "Incorrect value")
        }
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ValidationErrorResponse(
                HttpStatus.BAD_REQUEST, 
                "Method parameter validation error", 
                errors
            ))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(500, "Internal server error"))
    }
}
```

Порядок обработки: Spring ищет **наиболее конкретный** обработчик. `Exception::class` — это fallback, он сработает только если ни один другой не подошёл.

> Важно: в `handleUnexpected` не возвращаем `e.message` клиенту — оно может содержать внутренности системы. Вместо этого логируем полную ошибку (см. раздел про логгирование).

---

### 5) Валидация входных данных: @Valid, @Validated + jakarta.validation

Валидация — это проверка данных на входе в контроллер, **до** того как они попадут в сервисный слой.

**Зависимость в `pom.xml`:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

#### Аннотации на DTO

```kotlin
data class CreateRestaurantRequest(
    @field:NotBlank(message = "Название не может быть пустым")
    @field:Size(min = 2, max = 100, message = "Название: от 2 до 100 символов")
    val name: String,

    @field:NotBlank(message = "Адрес не может быть пустым")
    val address: String
)

data class CreateDishRequest(
    @field:NotBlank(message = "Название не может быть пустым")
    val name: String,

    @field:Min(value = 1, message = "Цена должна быть больше 0")
    val price: BigDecimal,

    val description: String? = null
)

data class CreateOrderRequest(
    @field:NotNull(message = "userId обязателен")
    val userId: Long,

    @field:NotEmpty(message = "Заказ должен содержать хотя бы одно блюдо")
    val dishIds: List<Long>
)
```

> Обратите внимание: в Kotlin нужно писать `@field:NotBlank`, а не просто `@NotBlank`. Без `@field:` аннотация попадёт на параметр конструктора, а не на поле, и Spring её не увидит.

#### @Valid vs @Validated

`@Valid` (jakarta) и `@Validated` (Spring) — две аннотации для включения валидации. Они похожи, но работают по-разному.

**`@Valid`** — ставится перед `@RequestBody`. Проверяет поля объекта:

```kotlin
@PostMapping
fun createRestaurant(@Valid @RequestBody request: CreateRestaurantRequest): ResponseEntity<RestaurantResponse> {
    // Если валидация не пройдена, Spring выбросит MethodArgumentNotValidException
    // до входа в тело метода. Его поймает наш GlobalExceptionHandler.
    val restaurant = restaurantService.create(request.toCommand())
    return ResponseEntity.status(HttpStatus.CREATED).body(restaurant.toResponse())
}
```

**`@Validated`** — ставится на **класс контроллера**. Позволяет валидировать `@PathVariable` и `@RequestParam` напрямую:

```kotlin
@RestController
@RequestMapping("/api/v1/restaurants")
@Validated
class RestaurantController(private val restaurantService: RestaurantService) {

    @GetMapping("/{id}")
    fun getById(@PathVariable @Min(1) id: Long): ResponseEntity<RestaurantResponse> {
        // Без @Validated на классе аннотация @Min на @PathVariable не сработает
        val restaurant = restaurantService.getById(id)
        return ResponseEntity.ok(restaurant.toResponse())
    }

    @GetMapping
    fun search(
        @RequestParam @Size(min = 2, message = "Минимум 2 символа для поиска") query: String?
    ): ResponseEntity<List<RestaurantResponse>> {
        // ...
    }
}
```

> При провале валидации через `@Validated` Spring выбросит `ConstraintViolationException` (а не `MethodArgumentNotValidException`). Его тоже нужно обработать в `GlobalExceptionHandler`.

```kotlin
@ExceptionHandler(ConstraintViolationException::class)
fun handleConstraintViolation(e: ConstraintViolationException): ResponseEntity<ErrorResponse> {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse(400, e.message))
}
```

| Что            | `@Valid`                          | `@Validated`                     |
|:---------------|:----------------------------------|:---------------------------------|
| Источник       | Jakarta (стандарт)                | Spring (расширение)              |
| Куда ставить   | Перед параметром метода           | На класс контроллера             |
| Что валидирует | `@RequestBody`                    | `@PathVariable`, `@RequestParam` |
| Исключение     | `MethodArgumentNotValidException` | `ConstraintViolationException`   |

На практике их используют **вместе**: `@Validated` на классе + `@Valid` перед `@RequestBody`.

#### Полезные аннотации

| Аннотация       | Назначение                            | Пример                                |
|:----------------|:--------------------------------------|:--------------------------------------|
| `@NotNull`      | Не null                               | `@field:NotNull`                      |
| `@NotBlank`     | Не null, не пустая, не только пробелы | `@field:NotBlank`                     |
| `@NotEmpty`     | Не null и не пустая коллекция/строка  | `@field:NotEmpty`                     |
| `@Size`         | Ограничение длины                     | `@field:Size(min = 2, max = 100)`     |
| `@Min` / `@Max` | Числовые границы                      | `@field:Min(1)`                       |
| `@Email`        | Проверка формата email                | `@field:Email`                        |
| `@Pattern`      | Регулярное выражение                  | `@field:Pattern(regexp = "^[A-Z].*")` |
| `@Positive`     | Число > 0                             | `@field:Positive`                     |

---

### 6) Логгирование

Логгирование — это запись событий, происходящих в приложении. Без логов невозможно диагностировать ошибки на проде.

Spring Boot использует `SLF4J` + `Logback` по умолчанию. Дополнительных зависимостей не нужно.

#### Создание логгера

Можно использовать `SLF4J` напрямую, но более предпочтительный подход в Kotlin — библиотека `kotlin-logging`. Она является обёрткой над SLF4J и даёт несколько преимуществ:
- **Лямбда-синтаксис** — строка лога не вычисляется, если уровень отключён (экономия ресурсов).
- **Kotlin-идиоматичный API** — никаких `{}` плейсхолдеров, обычная строковая интерполяция.
- **Компактнее** — не нужно передавать `Class` в `getLogger`.

**Зависимость в `pom.xml`:**

```xml
<dependency>
    <groupId>io.github.oshai</groupId>
    <artifactId>kotlin-logging-jvm</artifactId>
    <version>7.0.13</version>
</dependency>
```

**Использование:**

```kotlin
@Service
class RestaurantService(
    private val restaurantRepository: RestaurantRepositoryPort
) {
    private val logger = KotlinLogging.logger {}
    
    fun getById(id: Long): Restaurant {
        logger.info { "Запрос ресторана с id=$id" }
        return restaurantRepository.findById(id)
            ?: throw NotFoundException("Ресторан с id=$id не найден").also {
                logger.warn { "Ресторан с id=$id не найден" }
            }
    }

    fun create(command: CreateRestaurantCommand): Restaurant {
        val restaurant = restaurantRepository.save(command.toEntity())
        logger.info { "Создан ресторан: id=${restaurant.id}, name=${restaurant.name}" }
        return restaurant
    }
}
```

Для сравнения — тот же код на чистом SLF4J (более многословно):
```kotlin
private val logger = LoggerFactory.getLogger(RestaurantService::class.java)

logger.info("Запрос ресторана с id={}", id)  // плейсхолдеры вместо интерполяции
```

#### Уровни логирования

| Уровень | Когда использовать                                  |
|:--------|:----------------------------------------------------|
| `ERROR` | Что-то сломалось, требует внимания                  |
| `WARN`  | Нештатная ситуация, но приложение работает          |
| `INFO`  | Ключевые бизнес-события (создан заказ, удалён ресторан) |
| `DEBUG` | Детали для отладки (значения переменных, SQL)       |
| `TRACE` | Максимальная детализация (редко используется)       |

#### Настройка уровней в application.yaml

Простой способ — указать уровни прямо в `application.yaml`:

```yaml
logging:
  level:
    root: INFO
    com.example.delivery: DEBUG
    org.springframework.web: WARN
    org.hibernate.SQL: DEBUG
```

- `root` — общий уровень логирования для всего приложения (по умолчанию `INFO`).
- `com.example.delivery` — корневой пакет вашего проекта, для него включен `DEBUG`.
- Остальные пакеты можно переопределить по отдельности (`org.springframework.web: WARN` и т.д.).

#### Продвинутая настройка: logback-spring.xml

`application.yaml` подходит для простых случаев. Для более гибкой настройки (формат вывода, запись в файл, ротация логов) используется файл `logback-spring.xml` в `src/main/resources/`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!-- Вывод в консоль -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Вывод в файл с ротацией -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <!-- Новый файл каждый день -->
            <fileNamePattern>logs/app.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <!-- Максимальный размер одного файла -->
            <maxFileSize>10MB</maxFileSize>
            <!-- Хранить логи за последние 30 дней -->
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Уровни для пакетов -->
    <logger name="com.example.delivery" level="DEBUG"/>
    <logger name="org.springframework.web" level="WARN"/>
    <logger name="org.hibernate.SQL" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>

</configuration>
```

> Если `logback-spring.xml` присутствует, он **заменяет** настройки логирования из `application.yaml`. Не используйте оба способа одновременно.

Элементы паттерна:
- `%d{...}` — дата и время
- `%thread` — имя потока
- `%-5level` — уровень (INFO, WARN...), выровненный по 5 символам
- `%logger{36}` — имя логгера (обрезанное до 36 символов)
- `%msg%n` — сообщение и перенос строки

#### Логгирование в GlobalExceptionHandler

Особенно важно логировать непредвиденные ошибки — те, что попадают в fallback-обработчик:

```kotlin
@ExceptionHandler(Exception::class)
fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
    logger.error(e) { "Непредвиденная ошибка" }
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse(500, "Внутренняя ошибка сервера"))
}
```

> `logger.error(e) { ... }` — записывает сообщение **и** стек-трейс в лог, но клиенту возвращает только безопасное сообщение.

---

## Практическое задание

### 1) Создайте иерархию кастомных исключений

1. `sealed class AppException` — базовый класс.
2. `NotFoundException` — ресурс не найден.
3. `AlreadyExistsException` — конфликт (например, дублирование имени ресторана).
4. `InvalidOrderStateException` — недопустимый переход статуса заказа.

### 2) Реализуйте @RestControllerAdvice

Создайте `GlobalExceptionHandler`, который обрабатывает:

1. `NotFoundException` → `404 Not Found`.
2. `AlreadyExistsException` → `409 Conflict`.
3. `InvalidOrderStateException` → `400 Bad Request`.
4. `MethodArgumentNotValidException` → `400 Bad Request` с ошибками по полям.
5. `Exception` → `500 Internal Server Error` (fallback).

Все ответы — в едином формате (`ErrorResponse` / `ValidationErrorResponse`).

### 3) Добавьте валидацию на DTO

Используйте аннотации `jakarta.validation` на всех входных DTO:

1. `CreateRestaurantRequest` — `name` не пустое, `address` не пустой.
2. `CreateDishRequest` — `name` не пустое, `price` > 0.
3. `CreateOrderRequest` — `userId` не null, `dishIds` не пустой.
4. Используйте `@Valid` в контроллерах перед `@RequestBody`.

### 4) Выбросьте кастомные исключения из сервисов

Замените все места, где сервис возвращает `null` или бросает стандартные исключения:

1. `findById` → если не найдено, бросать `NotFoundException`.
2. Создание ресторана с дублирующимся именем → `AlreadyExistsException`.
3. Смена статуса заказа на недопустимый → `InvalidOrderStateException`.

### 5) Добавьте логгирование

1. Добавьте логгер в сервисный слой и в `GlobalExceptionHandler`.
2. Логируйте: создание/удаление сущностей (`INFO`), ошибки «не найдено» (`WARN`), непредвиденные ошибки (`ERROR` с трейсом).
3. Настройте уровни логирования через `logback-spring.xml`.
4. Настройте запись логов уровня `WARN` и `ERROR` в отдельный файл (appender `FILE`).

---

## Критерии оценки (максимум 10 баллов)

| Категория             | Критерий                                                     | Баллы  |
|:----------------------|:-------------------------------------------------------------|:------:|
| Штраф                 | Не проходят автотесты                                        |   -5   |
| Кастомные исключения  | Есть sealed-иерархия, используется в сервисах                |   1    |
| @RestControllerAdvice | Единый обработчик, корректные статусы (400/404/409/500)      |   2    |
| Валидация DTO         | `@Valid` / `@Validated` + аннотации на входных DTO           |   2    |
| Ошибки валидации      | `MethodArgumentNotValidException` возвращает ошибки по полям |   2    |
| Логгирование          | Логгер в сервисах и обработчике ошибок, настроены уровни     |   2    |
| Качество решения      | Единый формат ответа, чистота кода                           |   1    |
| **Итого**             |                                                              | **10** |

---

## Мини-чеклист перед сдачей

1. `POST` с невалидным телом возвращает `400` с перечнем ошибок по полям.
2. `GET /api/v1/restaurants/999999` возвращает `404` в едином формате, а не Spring-трейс.
3. Создание ресторана с дублирующимся именем возвращает `409`.
4. Непредвиденная ошибка возвращает `500` без стек-трейса в теле ответа.
5. В логах видны `INFO`/`WARN`/`ERROR` записи от вашего приложения.
6. Все прежние CRUD-эндпоинты из ЛР-4 по-прежнему работают.

---

## Что почитать

1. [Spring Boot Error Handling](https://www.baeldung.com/exception-handling-for-rest-with-spring)
2. [Bean Validation with Spring Boot](https://www.baeldung.com/spring-boot-bean-validation)
3. [Jakarta Validation Constraints](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html)
4. [SLF4J Manual](https://www.slf4j.org/manual.html)
5. [kotlin-logging](https://github.com/MicroUtils/kotlin-logging)
6. [Spring Boot Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
