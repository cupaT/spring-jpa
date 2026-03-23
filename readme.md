# Лабораторная работа №6

## Тестирование: unit, integration, Testcontainers, профили

---

## Цель работы

Покрыть проект доставки еды из ЛР-5 тестами: unit-тесты для сервисной логики (JUnit 5 + Mockito/MockK) и интеграционные тесты для API (MockMvc + Testcontainers). Настроить тестовый профиль приложения.

---

## Что нужно сдать

Ссылку на PR в ваш репозиторий (шаблон у вас есть).

---

## Теоретический блок

### 1) Автотесты и пирамида тестирования

Сейчас в вашем проекте проверка работает так: запустить приложение, отправить запрос через Postman или curl, посмотреть глазами. Проблемы:

- Это **медленно** — каждый раз нужно поднять приложение и БД.
- Это **ненадёжно** — легко забыть проверить пограничный случай.
- Это **нерегрессионно** — после изменения кода вы не знаете, что не сломали старое.

Автоматические тесты решают все три проблемы: запускаются за секунды, проверяют все сценарии каждый раз, ловят регрессии мгновенно.

#### Пирамида тестирования

![Тут должна была быть пирамида тестирования](.assets/test-pyramid.jpeg)

- **Unit-тесты** — проверяют один класс/метод в изоляции. Зависимости заменены моками. Быстрые, не требуют Spring-контекста и Docker.
- **Интеграционные тесты** — поднимают Spring-контекст, работают с реальной БД в контейнере, проверяют всю цепочку Controller → Service → Repository → БД.
- **E2E-тесты** — проверяют систему целиком (в рамках курса не делаем).

#### Где размещать тесты

Тесты живут в `src/test/kotlin/` — зеркальная структура основного кода:

```
src/
├── main/kotlin/com/example/delivery/
│   ├── service/
│   │   ├── RestaurantService.kt
│   │   └── OrderService.kt
│   └── controller/
│       └── RestaurantController.kt
└── test/kotlin/com/example/delivery/
    ├── service/
    │   ├── RestaurantServiceTest.kt      ← unit-тесты
    │   └── OrderServiceTest.kt           ← unit-тесты
    └── controller/
        └── RestaurantIntegrationTest.kt  ← интеграционные тесты
```

Конвенция: класс `RestaurantService` → тест `RestaurantServiceTest`.

---

### 2) Unit-тесты и JUnit 5

#### Что нужно добавить в проект

JUnit 5 уже входит в `spring-boot-starter-test` — дополнительных зависимостей не нужно:

```xml
<!-- pom.xml — уже должно быть -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Этот стартер включает: JUnit 5, Mockito, AssertJ, Hamcrest, JsonPath, MockMvc.

#### Аннотации JUnit 5

| Аннотация                    | Назначение                                                |
|:-----------------------------|:----------------------------------------------------------|
| `@Test`                      | Помечает тестовый метод                                   |
| `@BeforeEach` / `@AfterEach` | Выполняется до/после **каждого** теста                    |
| `@BeforeAll` / `@AfterAll`   | Выполняется до/после **всех** тестов в классе             |
| `@DisplayName`               | Человекочитаемое имя теста                                |
| `@Disabled`                  | Пропустить тест (с указанием причины)                     |
| `@ExtendWith`                | Подключить расширение (MockitoExtension, SpringExtension) |

> В Kotlin `@BeforeAll` и `@AfterAll` требуют `companion object` + `@JvmStatic`.

В Kotlin имена тестов можно писать в обратных кавычках — для кого-то это удобнее, чем `@DisplayName`. Решать вам:

```kotlin
@Test
fun `создание заказа с пустым списком блюд бросает исключение`() {
    // читается как спецификация
}
```

Или

```kotlin
@Test
@DisplayName("Создание заказа с пустым списком блюд бросает исключение")
fun emptyDishListOrderCreation() {
    // ну или так
}
```

#### Assertions

```kotlin
import org.junit.jupiter.api.Assertions.*

assertEquals(expected, actual)          // проверка равенства
assertNotNull(value)                    // не null
assertTrue(condition)                   // условие истинно
assertThrows<ExceptionType> { block }   // блок бросает исключение
```

#### Структура теста — Arrange / Act / Assert

Каждый тест следует паттерну:

```kotlin
@Test
fun `getById возвращает ресторан, если он существует`() {
    // Arrange — подготовка данных и моков
    val entity = RestaurantEntity(id = 1L, name = "Pizza Place", address = "ул. Ленина, 1")
    `when`(restaurantRepository.findById(1L)).thenReturn(Optional.of(entity))

    // Act — вызов тестируемого метода
    val result = restaurantService.getById(1L)

    // Assert — проверка результата
    assertEquals(1L, result.id)
    assertEquals("Pizza Place", result.name)
}
```

---

### 3) Мок-тестирование: Mockito и MockK

Unit-тест проверяет **один класс** в изоляции. Реальные зависимости (репозитории, другие сервисы) заменяются **моками** — объектами с запрограммированным поведением.

Два основных фреймворка мок-тестирования: **Mockito** (Java-стандарт, входит в `spring-boot-starter-test`) и **MockK** (нативный для Kotlin). Выбирайте любой — оба полностью поддерживаются Spring.

#### Вариант A: Mockito

Mockito уже входит в `spring-boot-starter-test` — дополнительных зависимостей не нужно.

**Основные концепции:**

| Что              | Как                                                    |
|:-----------------|:-------------------------------------------------------|
| Создать мок      | `@Mock` на поле                                        |
| Задать поведение | `` `when`(mock.method()).thenReturn(value) ``          |
| Проверить вызов  | `verify(mock).method()`                                |
| Внедрить моки    | `@InjectMocks` на тестируемом классе                   |
| Активировать     | `@ExtendWith(MockitoExtension::class)` на классе теста |

**Полный пример:**

```kotlin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class RestaurantServiceTest {

    @Mock
    lateinit var restaurantRepository: RestaurantJpaRepository

    @InjectMocks
    lateinit var restaurantService: RestaurantService

    @Test
    fun `getById возвращает ресторан, если он существует`() {
        val entity = RestaurantEntity(id = 1L, name = "Pizza Place", address = "ул. Ленина, 1")
        `when`(restaurantRepository.findById(1L)).thenReturn(Optional.of(entity))

        val result = restaurantService.getById(1L)

        assertEquals(1L, result.id)
        assertEquals("Pizza Place", result.name)
    }

    @Test
    fun `getById бросает NotFoundException для несуществующего id`() {
        `when`(restaurantRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            restaurantService.getById(999L)
        }
    }

    @Test
    fun `create бросает AlreadyExistsException при дублировании имени`() {
        `when`(restaurantRepository.existsByName("Pizza Place")).thenReturn(true)

        assertThrows<AlreadyExistsException> {
            restaurantService.create(CreateRestaurantCommand("Pizza Place", "ул. Мира, 5"))
        }

        verify(restaurantRepository, never()).save(any())
    }
}
```

> Неудобство Mockito в Kotlin: `` `when` `` нужно оборачивать в обратные кавычки, потому что `when` — ключевое слово Kotlin. `any()` иногда требует обёртки из-за nullable-типов.

#### Вариант B: MockK

MockK — мок-фреймворк, написанный специально для Kotlin. Нативно поддерживает `suspend`-функции, extension-функции и другие особенности языка.

**Зависимости:**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.mockk</groupId>
    <artifactId>mockk-jvm</artifactId>
    <version>1.13.17</version>          <!-- или другую версию -->
    <scope>test</scope>
</dependency>
```

**Основные концепции:**

| Что              | Как                                                  |
|:-----------------|:-----------------------------------------------------|
| Создать мок      | `@MockK` на поле или `mockk<Type>()`                 |
| Задать поведение | `every { mock.method() } returns value`              |
| Проверить вызов  | `verify { mock.method() }`                           |
| Внедрить моки    | `@InjectMockKs` на тестируемом классе                |
| Активировать     | `@ExtendWith(MockKExtension::class)` на классе теста |

**Тот же пример на MockK:**

```kotlin
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
class RestaurantServiceTest {

    @MockK
    lateinit var restaurantRepository: RestaurantJpaRepository

    @InjectMockKs
    lateinit var restaurantService: RestaurantService

    @Test
    fun `getById возвращает ресторан, если он существует`() {
        val entity = RestaurantEntity(id = 1L, name = "Pizza Place", address = "ул. Ленина, 1")
        every { restaurantRepository.findById(1L) } returns Optional.of(entity)

        val result = restaurantService.getById(1L)

        assertEquals(1L, result.id)
        assertEquals("Pizza Place", result.name)
    }

    @Test
    fun `getById бросает NotFoundException для несуществующего id`() {
        every { restaurantRepository.findById(999L) } returns Optional.empty()

        assertThrows<NotFoundException> {
            restaurantService.getById(999L)
        }
    }

    @Test
    fun `create бросает AlreadyExistsException при дублировании имени`() {
        every { restaurantRepository.existsByName("Pizza Place") } returns true

        assertThrows<AlreadyExistsException> {
            restaurantService.create(CreateRestaurantCommand("Pizza Place", "ул. Мира, 5"))
        }

        verify(exactly = 0) { restaurantRepository.save(any()) }
    }
}
```

#### Сравнение Mockito и MockK

| Критерий        | Mockito                               | MockK                           |
|:----------------|:--------------------------------------|:--------------------------------|
| Язык            | Java (адаптирован для Kotlin)         | Написан для Kotlin              |
| Зависимости     | Входит в `starter-test`               | Нужна отдельная зависимость     |
| Синтаксис       | `` `when`(...).thenReturn(...) ``     | `every { ... } returns ...`     |
| `when` в Kotlin | Нужны обратные кавычки (`` `when` ``) | Нет проблемы (`every`)          |
| Nullable-типы   | Иногда нужны обёртки для `any()`      | Работает из коробки             |
| Coroutines      | Ограниченная поддержка                | `coEvery`, `coVerify`           |
| Документация    | Огромная, много примеров              | Хорошая, Kotlin-ориентированная |

> Оба фреймворка прекрасно работают. Mockito — проще начать (уже есть в проекте). MockK — приятнее синтаксис в Kotlin. Выбирайте один и используйте его во всём проекте.

> Если выбрали MockK и используете его для интеграционных тестов со Spring, добавьте ещё `com.ninja-squad:springmockk` для замены `@MockBean` на `@MockkBean`.

---

### 4) Интеграционные тесты, MockMvc и Testcontainers

#### Зачем нужна реальная БД

Unit-тесты с моками не проверяют:
- Корректность SQL/JPQL запросов.
- Работу Flyway-миграций.
- Сериализацию/десериализацию JSON.
- Взаимодействие слоёв Controller → Service → Repository.

H2 (in-memory база) — не вариант: у неё другой SQL-диалект, другие типы данных, другое поведение. Тест зелёный на H2, а на проде с PostgreSQL — падает.

**Решение — Testcontainers**: запускает настоящий PostgreSQL в Docker-контейнере на время тестов.

#### Testcontainers vs docker-compose

Частый вопрос: «зачем Testcontainers, если у нас уже есть `docker-compose.yaml`?»

| Критерий     | docker-compose                      | Testcontainers                       |
|:-------------|:------------------------------------|:-------------------------------------|
| Запуск       | Ручной (`docker-compose up`)        | Автоматический (JUnit управляет)     |
| Порты        | Фиксированные → конфликты           | Случайные → изоляция                 |
| Состояние БД | Данные остаются между запусками     | Чистая БД каждый раз                 |
| CI/CD        | Нужен отдельный шаг в pipeline      | Запускается как часть `mvn test`     |
| Автоочистка  | Нужно помнить `docker-compose down` | Контейнер уничтожается автоматически |

`docker-compose.yaml` и Testcontainers — **не связаны**. Это разные инструменты:
- `docker-compose` — для локальной разработки и ручных тестов.
- Testcontainers — для автоматизированных тестов.

#### Зависимости для Testcontainers

```xml
<!-- pom.xml -->

<!-- Testcontainers: интеграция со Spring Boot -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>

<!-- Testcontainers: модуль PostgreSQL -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>

<!-- Testcontainers: интеграция с JUnit 5 -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

> Версии Testcontainers управляются через BOM Spring Boot — указывать `<version>` не нужно.

#### Профили приложения

Создайте `application-test.yaml` в `src/test/resources/`:

```yaml
# src/test/resources/application-test.yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

logging:
  level:
    root: WARN
    com.example.delivery: DEBUG
```

- `ddl-auto: validate` — Hibernate не меняет схему, только проверяет. Миграции делает Flyway.
- `root: WARN` — меньше шума в логах тестов.

> URL, username и password для БД не указываем — их подставит `@DynamicPropertySource` из Testcontainers.

#### Аннотации интеграционного тестового класса

| Аннотация                 | Что делает                                              |
|:--------------------------|:--------------------------------------------------------|
| `@SpringBootTest`         | Поднимает полный Spring-контекст                        |
| `@AutoConfigureMockMvc`   | Создаёт `MockMvc` для HTTP-тестов без реального сервера |
| `@Testcontainers`         | JUnit управляет Docker-контейнерами                     |
| `@Container`              | Помечает контейнер для автоматического lifecycle        |
| `@DynamicPropertySource`  | Передаёт параметры контейнера (URL, пароль) в Spring    |
| `@ActiveProfiles("test")` | Активирует тестовый профиль (`application-test.yaml`)   |
| `@Transactional`          | Откатывает изменения в БД после каждого теста           |

#### MockMvc: 3 стиля

`MockMvc` отправляет HTTP-запросы напрямую к контроллерам Spring — без запуска реального HTTP-сервера. Spring предоставляет два стиля API: стандартный (Java-style) и Kotlin DSL.

**Вариант A: Стандартный MockMvc**

Классический API с цепочкой `.perform().andExpect()`:

```kotlin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@Autowired
lateinit var mockMvc: MockMvc

@Test
fun `POST restaurant возвращает 201`() {
    mockMvc.perform(
        post("/api/v1/restaurants")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name": "New Place", "address": "ул. Тестовая, 1"}""")
    )
        .andExpect(status().isCreated)
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("New Place"))
}

@Test
fun `GET несуществующий ресторан возвращает 404`() {
    mockMvc.perform(get("/api/v1/restaurants/999999"))
        .andExpect(status().isNotFound)
        .andExpect(jsonPath("$.status").value(404))
}
```

Основные методы:
- `perform(get/post/put/delete(...))` — отправить запрос.
- `.contentType(...)` и `.content(...)` — тело запроса.
- `.andExpect(status().isOk)` — проверить HTTP-статус.
- `.andExpect(jsonPath("$.field").value(...))` — проверить поле JSON.

**Вариант B: MockMvc Kotlin DSL**

Spring Framework предоставляет Kotlin-расширения для MockMvc — более читаемый DSL-синтаксис. Дополнительных зависимостей не нужно, всё уже есть в `spring-boot-starter-test`.

```kotlin
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@Autowired
lateinit var mockMvc: MockMvc

@Test
fun `POST restaurant возвращает 201`() {
    mockMvc.post("/api/v1/restaurants") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"name": "New Place", "address": "ул. Тестовая, 1"}"""
    }.andExpect {
        status { isCreated() }
        jsonPath("$.id") { exists() }
        jsonPath("$.name") { value("New Place") }
    }
}

@Test
fun `GET несуществующий ресторан возвращает 404`() {
    mockMvc.get("/api/v1/restaurants/999999")
        .andExpect {
            status { isNotFound() }
            jsonPath("$.status") { value(404) }
        }
}
```

**Вариант C: REST Assured + spring-mock-mvc**

REST Assured — популярная библиотека для тестирования REST API с BDD-синтаксисом `given/when/then`. Модуль `spring-mock-mvc` позволяет использовать этот синтаксис поверх MockMvc — без запуска реального HTTP-сервера.

**Зависимость:**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>spring-mock-mvc</artifactId>
    <scope>test</scope>
</dependency>
```

> Этот модуль тянет за собой `spring-mock-mvc` и добавляет Kotlin-расширения.

**Настройка:** одна строка в `@BeforeEach` — связываем REST Assured с вашим `MockMvc`:

```kotlin
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import io.restassured.module.mockmvc.RestAssuredMockMvc

@Autowired
lateinit var mockMvc: MockMvc

@BeforeEach
fun setUp() {
    RestAssuredMockMvc.mockMvc(mockMvc)
}

@Test
fun `POST restaurant возвращает 201`() {
    Given {
        contentType(ContentType.JSON)
        body("""{"name": "New Place", "address": "ул. Тестовая, 1"}""")
    } When {
        post("/api/v1/restaurants")
    } Then {
        statusCode(201)
        body("id", notNullValue())
        body("name", equalTo("New Place"))
    }
}

@Test
fun `GET несуществующий ресторан возвращает 404`() {
    Given {
        // пустой блок — нет параметров
    } When {
        get("/api/v1/restaurants/999999")
    } Then {
        statusCode(404)
        body("status", equalTo(404))
    }
}
```

`RestAssuredMockMvc.mockMvc(mockMvc)` — переключает транспорт: REST Assured перестаёт отправлять реальные HTTP-запросы и направляет всё через MockMvc. Синтаксис `given/when/then` при этом сохраняется.

#### Сравнение стилей MockMvc

| Критерий    | Стандартный                                            | Kotlin DSL                             | REST Assured                             |
|:------------|:-------------------------------------------------------|:---------------------------------------|:-----------------------------------------|
| Импорт      | `MockMvcRequestBuilders.*` + `MockMvcResultMatchers.*` | `spring.test.web.servlet.get/post/...` | `io.restassured.module.mockmvc.kotlin.*` |
| Синтаксис   | `.perform(post(...)).andExpect(...)`                   | `.post("/...") { }.andExpect { }`      | `Given { } When { } Then { }`            |
| Зависимости | Входит в `starter-test`                                | Входит в `starter-test`                | Нужна отдельная зависимость              |
| Читаемость  | Многословнее, но привычнее                             | Компактнее, DSL-стиль                  | BDD-стиль, самый читаемый                |

> Все три варианта работают поверх одного и того же MockMvc. Выбирайте один и используйте во всём проекте.

#### Полный пример интеграционного теста (стандартный MockMvc)

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RestaurantIntegrationTest {

    companion object {
        @Container // Управляет стартом/стопом контейнера
        @ServiceConnection // МАГИЯ: Автоматически прокидывает настройки в Spring DataSource
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            // Опционально: можно задать имя БД или пароль, 
            // но благодаря @ServiceConnection Спрингу всё равно, какие они
            withDatabaseName("integration-tests-db")
            withUsername("test")
            withPassword("test")
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `POST restaurant возвращает 201 и создаёт запись`() {
        mockMvc.perform(
            post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "New Place", "address": "ул. Тестовая, 1"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("New Place"))
            .andExpect(jsonPath("$.address").value("ул. Тестовая, 1"))
    }

    @Test
    fun `GET несуществующий ресторан возвращает 404`() {
        mockMvc.perform(get("/api/v1/restaurants/999999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `POST restaurant с пустым именем возвращает 400 и errors`() {
        mockMvc.perform(
            post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "", "address": "ул. Тестовая, 1"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.name").exists())
    }
}
```

**Что здесь происходит:**
1. `@Testcontainers` + `@Container` — JUnit запускает PostgreSQL в Docker перед тестами.
2. `@DynamicPropertySource` — передаёт случайный URL, логин и пароль контейнера в Spring.
3. Flyway автоматически применяет миграции к чистой БД.
4. `MockMvc` отправляет запросы через все слои: Controller → Service → Repository → PostgreSQL.
5. После тестов контейнер автоматически уничтожается.

> `companion object` + `@JvmStatic` нужны потому, что `@DynamicPropertySource` и `@BeforeAll` в Kotlin требуют статический метод.

#### Полный пример интеграционного теста (Kotlin DSL)

Тот же класс, но с Kotlin DSL для MockMvc:

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class RestaurantIntegrationTest {

    companion object {
        @Container // Управляет стартом/стопом контейнера
        @ServiceConnection // МАГИЯ: Автоматически прокидывает настройки в Spring DataSource
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            // Опционально: можно задать имя БД или пароль, 
            // но благодаря @ServiceConnection Спрингу всё равно, какие они
            withDatabaseName("integration-tests-db")
            withUsername("test")
            withPassword("test")
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `POST restaurant возвращает 201 и создаёт запись`() {
        mockMvc.post("/api/v1/restaurants") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "New Place", "address": "ул. Тестовая, 1"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.name") { value("New Place") }
            jsonPath("$.address") { value("ул. Тестовая, 1") }
        }
    }

    @Test
    fun `GET несуществующий ресторан возвращает 404`() {
        mockMvc.get("/api/v1/restaurants/999999")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.status") { value(404) }
            }
    }

    @Test
    fun `POST restaurant с пустым именем возвращает 400 и errors`() {
        mockMvc.post("/api/v1/restaurants") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "", "address": "ул. Тестовая, 1"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.status") { value(400) }
            jsonPath("$.errors.name") { exists() }
        }
    }
}
```

#### Полный пример интеграционного теста (REST Assured)

Тот же класс, но с REST Assured `spring-mock-mvc`:

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class RestaurantIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("integration-tests-db")
            withUsername("test")
            withPassword("test")
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc)
    }

    @Test
    fun `POST restaurant возвращает 201 и создаёт запись`() {
        Given {
            contentType(ContentType.JSON)
            body("""{"name": "New Place", "address": "ул. Тестовая, 1"}""")
        } When {
            post("/api/v1/restaurants")
        } Then {
            statusCode(201)
            body("id", notNullValue())
            body("name", equalTo("New Place"))
            body("address", equalTo("ул. Тестовая, 1"))
        }
    }

    @Test
    fun `GET несуществующий ресторан возвращает 404`() {
        Given {
        } When {
            get("/api/v1/restaurants/999999")
        } Then {
            statusCode(404)
            body("status", equalTo(404))
        }
    }

    @Test
    fun `POST restaurant с пустым именем возвращает 400 и errors`() {
        Given {
            contentType(ContentType.JSON)
            body("""{"name": "", "address": "ул. Тестовая, 1"}""")
        } When {
            post("/api/v1/restaurants")
        } Then {
            statusCode(400)
            body("status", equalTo(400))
            body("errors.name", notNullValue())
        }
    }
}
```

#### Запуск тестов

```bash
# Все тесты
./mvnw test

# Только unit-тесты (по имени класса)
./mvnw test -Dtest="*ServiceTest"

# Только интеграционные тесты
./mvnw test -Dtest="*IntegrationTest"

# Один конкретный тест
./mvnw test -Dtest="RestaurantServiceTest#getById возвращает ресторан, если он существует"
```

> Для запуска интеграционных тестов Docker должен быть запущен — Testcontainers использует его для создания контейнеров.

---

## Практическое задание

### В этой лабораторной не будет готовых автотестов.
> вы их напишете сами 😁

### 1) Настройте тестовый профиль

1. Создайте `src/test/resources/application.yaml`.
2. Укажите `ddl-auto: validate`, включите Flyway, уменьшите уровень логирования.

### 2) Напишите unit-тесты для сервисного слоя

Покройте unit-тестами **сервисы** в своем проекте. Для каждого сервиса создайте отдельный тестовый класс. Используйте Mockito **или** MockK — на ваш выбор.  
У каждого теста должно быть или понятное читаемое название, или он должен использовать аннотацию `@Display`

> Каждый тест должен использовать моки. Не поднимайте Spring-контекст в unit-тестах (нет `@SpringBootTest`).

### 3) Напишите интеграционные тесты для API

Создайте интеграционный тестовый класс с `Testcontainers` + `MockMvc`. Покройте минимум **один контроллер** (рекомендуется `RestaurantController`). Используйте стандартный `MockMvc`, `Kotlin DSL` **или** `REST Assured` — на ваш выбор.

Для каждого эндпоинта сделайте проверку как `позитивных`, так и `негативных` сценариев.

> Все тесты должны проверять не только HTTP-статус, но и тело ответа через.

### 4) Убедитесь, что все тесты проходят

Все тесты должны быть зелёными. Docker должен быть запущен для интеграционных тестов.

Аналогично предыдущим работам, вам предложен `ci.yaml`, запускающий ваши тесты при `PR`.

---

## Критерии оценки (максимум 15 баллов)

| Категория            | Критерий                                                 | Баллы  |
|:---------------------|:---------------------------------------------------------|:------:|
| Тестовый профиль     | `application.yaml` / `@ActiveProfiles("test")`           |   1    |
| Unit-тесты           | Mockito или MockK                                        |   6    |
| Интеграционные тесты | Testcontainers + MockMvc, позитивные и негативные        |   6    |
| Качество тестов      | Arrange/Act/Assert, понятные имена, проверка тела ответа |   2    |
| `./mvnw test`        | Все тесты проходят без ошибок                            |   2    |
| **Итого**            |                                                          | **15** |

---

## Мини-чеклист перед сдачей

1. `mvn test` проходит без ошибок (Docker запущен).
2. Unit-тесты не поднимают Spring-контекст (нет `@SpringBootTest`).
3. Интеграционные тесты используют `Testcontainers`, а не H2.
4. Есть `application.yaml` с тестовым профилем.
5. Тесты проверяют и позитивные, и негативные сценарии.
6. Каждый тест проверяет тело ответа, а не только HTTP-статус.
7. Все тесты из ЛР-5 (`run-test.sh`) по-прежнему проходят при ручной проверке.

---

## Что почитать

1. [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
2. [Mockito Documentation](https://site.mockito.org/)
3. [MockK Documentation](https://mockk.io/)
4. [Spring Boot Testing](https://docs.spring.io/spring-boot/reference/testing/index.html)
5. [Spring MockMvc](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html)
6. [Testcontainers for Java](https://java.testcontainers.org/)
7. [Testcontainers — PostgreSQL Module](https://java.testcontainers.org/modules/databases/postgres/)
8. [Baeldung — Spring Boot Testing](https://www.baeldung.com/spring-boot-testing)
9. [Baeldung — MockK](https://www.baeldung.com/kotlin/mockk)
10. [REST Assured — spring-mock-mvc](https://github.com/rest-assured/rest-assured/wiki/Usage#spring-mock-mvc-module)
11. [Baeldung — REST Assured](https://www.baeldung.com/rest-assured-tutorial)
