# Лабораторная работа №3
## CRUD-приложение на Kotlin + Spring Boot + H2

---

## Цель работы
С нуля реализовать CRUD-приложение для двух **независимых** сущностей (`User`, `Dish`) с использованием подхода `Onion/Clean Architecture`, а также двух источников данных:
1. Mock-репозитории (in-memory).
2. Репозитории на `Spring Data JPA` + `H2`.

Важно: связи между сущностями в этой работе **не делаем** (они вынесены в ЛР-4).

---

## Что нужно сдать
Ссылку на PR в ваш репозиторий (созданный через этот шаблон).

---

## Теоретический блок

### 1) Что такое H2 и как подключить
`H2` — это легковесная Java СУБД. Она может работать как in-memory или как файловая БД.  
В этой работе используем **файловый режим**, чтобы данные сохранялись между перезапусками приложения.

**Плюсы для ЛР:**
1. Быстрый старт.
2. Простая интеграция со Spring Boot.
3. Удобна для автотестов и локальной отладки.

**Зависимость в `pom.xml`:**
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

Обычно вместе с ней в проекте уже должны быть:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

**Пример конфигурации `application.yml` (файловая H2):**
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/lab3db;MODE=PostgreSQL;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  h2:
    console:
      enabled: true
      path: /h2-console
```

---

### 2) Spring Data JPA: два подхода в этой работе
`Spring Data JPA` позволяет быстро реализовывать доступ к данным через интерфейсы репозиториев.

#### Подход A: производные методы (Derived Query Methods)
Spring сам генерирует SQL по имени метода.

```kotlin
interface UserJpaRepository : JpaRepository<UserEntity, Long> {
    fun findByEmail(email: String): UserEntity?
    fun existsByEmail(email: String): Boolean
    fun findAllByActiveTrue(): List<UserEntity>
}
```

#### Подход B: JPQL-запросы (`@Query`)
Используется, когда нужна более гибкая выборка.

```kotlin
interface DishJpaRepository : JpaRepository<DishEntity, Long> {

    @Query(
        """
        select d
        from DishEntity d
        where d.isAvailable = true
          and d.price <= :maxPrice
        order by d.price asc
        """
    )
    fun findAvailableCheaperThan(maxPrice: BigDecimal): List<DishEntity>
}
```

Это **демонстрационный** пример JPQL.  
В решении ориентируйтесь на контракт из `spec.yaml`, а не копируйте этот фрагмент один в один.

---

### 3) Архитектура (Onion/Clean) при нескольких поставщиках данных
Когда у нас есть `mock` и `db`, бизнес-логика не должна зависеть от конкретного источника.  
Решение: зависимости направляем к абстракциям (портам).

#### 3.1 Порт (доменный интерфейс)
```kotlin
interface UserRepositoryPort {
    fun create(user: User): User
    fun findById(id: Long): User?
    // ...
}
```

#### 3.2 Mock-адаптер (in-memory)
```kotlin
class UserMockRepository : UserRepositoryPort {
    private val storage = mutableMapOf<Long, User>()
    private var seq = 1L

    override fun create(user: User): User {
        val saved = user.copy(id = seq++)
        storage[saved.id] = saved
        return saved
    }

    // ...
}
```

#### 3.3 JPA-адаптер (через Spring Data)
```kotlin
class UserJpaAdapter(
    private val userJpaRepository: UserJpaRepository
) : UserRepositoryPort {
    override fun create(user: User): User =
        userJpaRepository.save(UserEntity.fromDomain(user)).toDomain()

    // ...
}
```

#### 3.4 Service не знает, откуда пришли данные
```kotlin
@Service
class UserService(
    private val userRepositoryPort: UserRepositoryPort
) {
    fun execute(cmd: CreateUserCommand): User {
        return userRepositoryPort.create(
            User(
                id = 0,
                email = cmd.email,
                firstName = cmd.firstName,
                lastName = cmd.lastName,
                isActive = true
            )
        )
    }
}
```

#### 3.5 Переключение поставщика
Переключение можно сделать:
1. Через `@Profile("mock")` / `@Profile("db")`.
2. Через конфигурационное свойство `app.data-provider=mock|db`.

Главный принцип: меняется только адаптер, service остается неизменным.

---

## Практическое задание

### 1) Реализуйте приложение с двумя сущностями
Сущности должны быть независимыми, без связей.

#### `User`
1. `id: Long`
2. `email: String`
3. `firstName: String`
4. `lastName: String`
5. `isActive: Boolean`

#### `Dish`
1. `id: Long`
2. `name: String`
3. `description: String`
4. `price: BigDecimal`
5. `isAvailable: Boolean`

### 2) Реализуйте endpoint'ы строго по `spec.yaml`
Нужно реализовать **ровно те endpoint'ы и контракты**, которые описаны в спецификации (методы, URL, параметры, коды ответов, схемы JSON).

Кратко, что есть в спецификации:
1. CRUD для `users`.
2. CRUD для `dishes`.
3. Для `GET /api/v1/dishes` поддержка опционального фильтра `namePart` (для JPQL-выборки).

### 3) Этап 1: Mock-репозитории
1. Создайте порты (`UserRepositoryPort`, `DishRepositoryPort`) в domain/application слое.
2. Реализуйте mock-адаптеры (in-memory) для обоих портов.
3. Поднимите API целиком на mock-реализации и проверьте CRUD.

### 4) Этап 2: JPA + H2
1. Подключите `H2` и настройте datasource.
2. Создайте `Entity`-классы и Spring Data репозитории.
3. Реализуйте JPA-адаптеры, которые реализуют те же порты.
4. Переключите приложение на JPA-репозитории.

### 5) Обязательное требование по репозиториям
1. `User`-репозиторий: использовать **derived methods**.
2. `Dish`-репозиторий: добавить минимум **один JPQL `@Query`** метод.

### 6) Архитектурное требование
Приложение должно быть организовано по `Onion/Clean Architecture`:
1. HTTP-контроллеры не зависят от JPA-сущностей напрямую.
2. Service-слой не зависит от Spring Data.
3. Инфраструктура подключается как адаптеры к портам.

---

## Критерии оценки (максимум 15 баллов)

| Категория           | Критерий                                                     | Баллы  |
|:--------------------|:-------------------------------------------------------------|:------:|
| Штраф               | Не проходят автотесты                                        |   -7   |
| Архитектура         | Реализована Onion/Clean структура: порты, services, адаптеры |   4    |
| CRUD: User          | Полный CRUD для `User` работает корректно                    |   2    |
| CRUD: Dish          | Полный CRUD для `Dish` работает корректно                    |   2    |
| Mock-слой           | Есть рабочие mock-репозитории для двух сущностей             |   2    |
| JPA-слой            | Реализованы JPA-адаптеры и работа с H2                       |   2    |
| Spring Data подходы | Один репозиторий на derived methods, второй с JPQL `@Query`  |   2    |
| Качество решения    | Чистота кода, структура пакетов, читаемость                  |   1    |
| **Итого**           |                                                              | **15** |

---

## Мини-чеклист перед сдачей
1. Проект запускается локально.
2. В коде есть и mock, и JPA реализации репозиториев.
3. Для `Dish` есть минимум один JPQL запрос.
4. CRUD для `User` и `Dish` работает через HTTP.

---

## Что почитать
1. [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/)
2. [H2 Database](https://www.h2database.com/html/main.html)
3. [Spring Boot Data JPA Guide](https://spring.io/guides/gs/accessing-data-jpa/)
4. [Чистая архитектура](https://education.yandex.ru/handbook/flutter/article/clean-architecture)
5. [Spring Data JPA @Query](https://www.baeldung.com/spring-data-jpa-query)
6. [Spring Data H2](https://www.baeldung.com/spring-boot-h2-database)
7. [Spring API versioning](https://spring.io/blog/2025/09/16/api-versioning-in-spring)
8. [Spring API versioning again](https://www.baeldung.com/spring-api-versioning#whats-new-in-spring-framework-7-amp-boot-4)
