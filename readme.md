# Лабораторная работа №7

## Безопасность: аутентификация и авторизация (2 занятия)

---

## Цель работы

Добавить в проект доставки еды из ЛР-6: аутентификацию пользователей (регистрация, логин, JWT), авторизацию по ролям (`USER`, `ADMIN`) и защиту эндпоинтов с помощью Spring Security.

---

## Что нужно сдать

Ссылку на PR в ваш репозиторий (шаблон у вас есть).

---

## Теоретический блок

### 1) Зачем защищать API

Сейчас ваш API полностью открыт: любой, кто знает URL, может создать ресторан, удалить блюдо или посмотреть чужие заказы. В реальной системе это недопустимо.

Две ключевые задачи:
- **Аутентификация** — *кто* отправил запрос? Проверяем личность пользователя (логин + пароль, токен).
- **Авторизация** — *что* ему разрешено? Проверяем, имеет ли пользователь право на конкретное действие.

Аналогия: аутентификация — это паспортный контроль на входе в здание, авторизация — это пропуск, определяющий, на какие этажи вы можете попасть.

---

### 2) Stateful vs Stateless

Есть два основных подхода к аутентификации в веб-приложениях:

| | Stateful (сессии) | Stateless (токены) |
|:--|:--|:--|
| Где хранится состояние | На сервере (в памяти, Redis, БД) | На клиенте (в токене) |
| Что получает клиент | Session ID в cookie | JWT в теле ответа |
| Как клиент себя идентифицирует | Cookie с session ID | Заголовок `Authorization: Bearer <token>` |
| Горизонтальное масштабирование | Сложнее (нужна общая сессия) | Просто (каждый сервер проверяет токен сам) |
| Подходит для | Классические веб-приложения | REST API, мобильные клиенты, SPA |

В этой лабораторной мы реализуем **stateless**-подход с JWT — он лучше подходит для REST API.

---

### 3) JWT: структура и жизненный цикл

JWT (JSON Web Token) — это самодостаточный токен, содержащий информацию о пользователе. Он состоит из трёх частей, разделённых точками:

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQG1haWwuY29tIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3MTE...
\_______ Header _______/ \________________________ Payload _________________________/ \_ Signature _/
```

- **Header** — алгоритм подписи (`HS256`, `RS256`).
- **Payload** — данные (claims): кто (`sub`), роль, время выдачи (`iat`), время истечения (`exp`).
- **Signature** — подпись, гарантирующая, что токен не был изменён.

> Header и Payload — это просто Base64-кодированный JSON. Любой может их прочитать. Подпись не шифрует данные — она лишь **защищает от подделки**. Поэтому не храните в JWT пароли или чувствительные данные.

#### Жизненный цикл

1. Клиент отправляет `POST /auth/login` с логином и паролем.
2. Сервер проверяет credentials, генерирует JWT и возвращает его в теле ответа.
3. Клиент сохраняет токен и отправляет его в заголовке `Authorization: Bearer <token>` с каждым запросом.
4. Сервер проверяет подпись и срок действия токена при каждом запросе.
5. Когда токен истекает, клиент запрашивает новый (через повторный логин или refresh-токен).

---

### 4) Spring Security: архитектура

Spring Security работает через **цепочку фильтров** (Security Filter Chain). Каждый HTTP-запрос проходит через эту цепочку до того, как попадёт в контроллер.

```
HTTP Request
    │
    ▼
┌─────────────────────┐
│  Security Filter Chain │
│  ┌──────────────────┐│
│  │ CORS Filter      ││
│  ├──────────────────┤│
│  │ CSRF Filter      ││ ← отключим для stateless
│  ├──────────────────┤│
│  │ JWT Auth Filter  ││ ← наш кастомный фильтр
│  ├──────────────────┤│
│  │ Authorization    ││
│  │ Filter           ││
│  └──────────────────┘│
└─────────────────────┘
    │
    ▼
  Controller
```

Ключевые компоненты, которые нам понадобятся:
- `SecurityFilterChain` — конфигурация: какие URL открыты, какие закрыты.
- `UserDetailsService` — как загружать пользователя из БД.
- `PasswordEncoder` — как хешировать и проверять пароли.
- `JwtAuthenticationFilter` — наш кастомный фильтр, парсящий JWT из заголовка.

---

### 5) Сущность пользователя и роли

Для работы с аутентификацией нужна сущность `User` с ролями. В нашем проекте доставки достаточно двух ролей:
- `USER` — может просматривать рестораны, меню и создавать заказы.
- `ADMIN` — может управлять ресторанами и блюдами.

Для хранения ролей есть два подхода — выберите один из них.

#### Вариант A: роль как enum-поле в таблице `users`

Простой подход — роль хранится прямо в колонке таблицы пользователей:

```kotlin
enum class Role {
    USER, ADMIN
}

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(nullable = false)
    val password: String,  // BCrypt-хеш, не plain text!

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role = Role.USER
)
```

Плюсы: просто, один запрос для загрузки пользователя. Минусы: один пользователь — одна роль, расширяемость ограничена.

#### Вариант B: отдельная таблица ролей (many-to-many)

Более гибкий подход — роли хранятся в отдельной таблице, связанной с пользователем через `@ManyToMany`:

```kotlin
@Entity
@Table(name = "roles")
class RoleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val name: String  // "ROLE_USER", "ROLE_ADMIN"
)

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(nullable = false)
    val password: String,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    val roles: Set<RoleEntity> = emptySet()
)
```

Плюсы: пользователь может иметь несколько ролей, легко добавлять новые роли без изменения схемы. Минусы: сложнее, дополнительный JOIN при загрузке.

> Таблица называется `users`, а не `user`, потому что `user` — зарезервированное слово в PostgreSQL.

> Сущность `User` в вашем проекте уже существует с предыдущих лабораторных — вам нужно **расширить** её полями `password` и `role` (или связью с таблицей ролей), а не создавать заново. Не забудьте написать Flyway-миграцию для изменения таблицы.

---

### 6) Хеширование паролей: BCrypt

Пароли **никогда** не хранятся в открытом виде. Используем BCrypt — алгоритм хеширования, специально разработанный для паролей:

```kotlin
@Bean
fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
```

BCrypt автоматически добавляет *соль* (случайную строку) к каждому паролю, поэтому два одинаковых пароля дадут разные хеши. Это защищает от атак по таблицам (rainbow tables).

```kotlin
// При регистрации
val hashedPassword = passwordEncoder.encode(rawPassword)
// hashedPassword = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"

// При логине
passwordEncoder.matches(rawPassword, hashedPassword) // true/false
```

---

### 7) UserDetailsService: загрузка пользователя

Spring Security использует интерфейс `UserDetailsService` для загрузки данных пользователя при аутентификации. Есть два подхода — выберите один.

#### Вариант A: делегирование к `User.builder()`

Простой подход — загружаем сущность из БД и конвертируем в стандартный `UserDetails`:

```kotlin
@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
            ?: throw UsernameNotFoundException("Пользователь не найден: $username")

        return User.builder()
            .username(user.email)
            .password(user.password)
            .roles(user.role.name)
            .build()
    }
}
```

> `User.builder()` — это `org.springframework.security.core.userdetails.User`, не путать с вашей сущностью.

Плюсы: просто, нет дополнительных классов. Минусы: теряется связь с доменной сущностью — из `UserDetails` нельзя получить `id` или другие поля вашего `UserEntity`.

#### Вариант B: кастомная реализация `UserDetails`

Ваша сущность сама реализует интерфейс `UserDetails`:

```kotlin
@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(nullable = false)
    private val password: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role = Role.USER
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_${role.name}"))

    override fun getPassword(): String = password
    override fun getUsername(): String = email
}
```

```kotlin
@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        return userRepository.findByEmail(username)
            ?: throw UsernameNotFoundException("Пользователь не найден: $username")
    }
}
```

Плюсы: через `@AuthenticationPrincipal` можно получить не только `username`, но и `id`, `role` и любые другие поля сущности напрямую. Минусы: доменная сущность связана с интерфейсом Spring Security.

---

### 8) JwtService: генерация и валидация токенов

Для работы с JWT используем библиотеку `jjwt`. Добавьте зависимости:

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

```kotlin
@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-ms}") private val expirationMs: Long
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    fun generateToken(email: String, role: String): String {
        return Jwts.builder()
            .subject(email)
            .claim("role", role)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(key)
            .compact()
    }

    fun extractEmail(token: String): String {
        return extractAllClaims(token).subject
    }

    fun extractRole(token: String): String {
        return extractAllClaims(token)["role"] as String
    }

    fun isTokenValid(token: String): Boolean {
        return try {
            val claims = extractAllClaims(token)
            !claims.expiration.before(Date())
        } catch (e: JwtException) {
            false
        }
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
```

Настройки в `application.yaml`:

```yaml
jwt:
  secret: ${JWT_SECRET}
  expiration-ms: ${JWT_EXPIRATION:86400000}  # 24 часа по умолчанию
```

> Секретный ключ JWT **не должен** быть захардкожен в `application.yaml` — это чувствительные данные, которые попадут в git. Конструкция `${JWT_EXPIRATION:86400000}` задаёт значение по умолчанию после двоеточия — если переменная не задана, будет использовано `86400000`.
>
> Для локальной разработки задайте переменные в `.env`-файле (добавьте его в `.gitignore`) или в конфигурации запуска IDE.

---

### 9) JwtAuthenticationFilter: кастомный фильтр

Это ключевой компонент — фильтр, который перехватывает каждый запрос, извлекает JWT из заголовка `Authorization` и устанавливает аутентификацию в `SecurityContext`:

```kotlin
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        // Если заголовка нет или он не начинается с "Bearer " — пропускаем
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(7) // убираем "Bearer "

        if (jwtService.isTokenValid(token)) {
            val email = jwtService.extractEmail(token)
            val userDetails = userDetailsService.loadUserByUsername(email)

            val authentication = UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.authorities
            )
            authentication.details = WebAuthenticationDetailsSource()
                .buildDetails(request)

            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }
}
```

Логика фильтра:
1. Достаём заголовок `Authorization`.
2. Если его нет или он не содержит `Bearer` — пропускаем запрос дальше (он может быть на публичный URL).
3. Извлекаем токен, проверяем валидность (подпись + срок).
4. Если токен валиден — загружаем пользователя и устанавливаем аутентификацию.
5. Всегда вызываем `filterChain.doFilter()` — запрос идёт дальше по цепочке.

---

### 10) SecurityFilterChain: конфигурация

Теперь собираем всё вместе в конфигурационном классе:

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // включает @PreAuthorize
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }  // CSRF не нужен для stateless API
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }
            authorizeHttpRequests {
                authorize("/auth/**", permitAll)   // регистрация и логин открыты
                authorize(HttpMethod.GET, "/api/v1/restaurants/**", permitAll) // просмотр открыт
                authorize(anyRequest, authenticated) // остальное — только для аутентифицированных
            }
            addFilterBefore<UsernamePasswordAuthenticationFilter>(jwtAuthenticationFilter)
        }

        return http.build()
    }

    @Bean
    fun authenticationManager(
        config: AuthenticationConfiguration
    ): AuthenticationManager {
        return config.authenticationManager
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
```

Ключевые решения:
- **CSRF отключен** — он нужен для форм с cookie-сессиями, а мы используем stateless JWT.
- **Session policy = STATELESS** — Spring Security не будет создавать HTTP-сессии.
- **`/auth/**` открыт** — без этого невозможно зарегистрироваться или залогиниться.
- **JWT-фильтр добавлен перед стандартным фильтром** — он работает до проверки авторизации.

> Этот пример использует Kotlin DSL для Spring Security (`http { ... }`). Он доступен начиная с Spring Security 6.x и Spring Boot 3.x.

---

### 11) Контроллер аутентификации

```kotlin
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }
}
```

DTO:

```kotlin
data class RegisterRequest(
    @field:Email(message = "Некорректный формат email")
    @field:NotBlank(message = "Email обязателен")
    val email: String,

    @field:NotBlank(message = "Пароль обязателен")
    @field:Size(min = 6, message = "Пароль должен быть не менее 6 символов")
    val password: String,

    @field:NotBlank(message = "Имя обязательно")
    val name: String
)

data class LoginRequest(
    @field:NotBlank(message = "Email обязателен")
    val email: String,

    @field:NotBlank(message = "Пароль обязателен")
    val password: String
)

data class AuthResponse(
    val token: String,
    val email: String,
    val role: String
)
```

---

### 12) Сервис аутентификации

```kotlin
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager
) {
    private val logger = KotlinLogging.logger {}

    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw AlreadyExistsException("Пользователь с email ${request.email} уже существует")
        }

        val user = UserEntity(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            name = request.name,
            role = Role.USER
        )

        val savedUser = userRepository.save(user)
        logger.info { "Зарегистрирован пользователь: ${savedUser.email}" }

        val token = jwtService.generateToken(savedUser.email, savedUser.role.name)
        return AuthResponse(token, savedUser.email, savedUser.role.name)
    }

    fun login(request: LoginRequest): AuthResponse {
        // AuthenticationManager проверяет credentials через UserDetailsService + PasswordEncoder.
        // Более оптимальный подход — использовать результат authenticate():
        // он возвращает Authentication, из которого можно получить UserDetails через getPrincipal(),
        // и не ходить повторно в БД за пользователем.
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )

        val user = userRepository.findByEmail(request.email)
            ?: throw NotFoundException("Пользователь не найден")

        logger.info { "Вход пользователя: ${user.email}" }

        val token = jwtService.generateToken(user.email, user.role.name)
        return AuthResponse(token, user.email, user.role.name)
    }
}
```

> Обратите внимание: `authenticationManager.authenticate()` выбросит `BadCredentialsException`, если пароль неверен. Это исключение нужно обработать в `GlobalExceptionHandler`.

---

### 13) Защита эндпоинтов по ролям: @PreAuthorize

`@PreAuthorize` позволяет указать, какие роли имеют доступ к конкретному методу контроллера:

```kotlin
@RestController
@RequestMapping("/api/v1/restaurants")
class RestaurantController(
    private val restaurantService: RestaurantService
) {

    @GetMapping
    fun getAll(): ResponseEntity<List<RestaurantResponse>> {
        // Доступно всем (настроено в SecurityFilterChain)
        return ResponseEntity.ok(restaurantService.getAll().map { it.toResponse() })
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun create(@Valid @RequestBody request: CreateRestaurantRequest): ResponseEntity<RestaurantResponse> {
        // Только ADMIN может создавать рестораны
        val restaurant = restaurantService.create(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurant.toResponse())
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(@PathVariable id: Long): ResponseEntity<Unit> {
        restaurantService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
```

Матрица доступа для сервиса доставки:

| Эндпоинт                               | USER | ADMIN | Без авторизации |
|:---------------------------------------|:----:|:-----:|:---------------:|
| `POST /auth/register`                  |  -   |   -   |        +        |
| `POST /auth/login`                     |  -   |   -   |        +        |
| `GET /api/v1/restaurants`              |  +   |   +   |        +        |
| `GET /api/v1/restaurants/{id}/dishes`  |  +   |   +   |        +        |
| `POST /api/v1/restaurants`             |  -   |   +   |        -        |
| `POST /api/v1/restaurants/{id}/dishes` |  -   |   +   |        -        |
| `PUT, DELETE` ресторанов и блюд        |  -   |   +   |        -        |
| `POST /api/v1/orders`                  |  +   |   +   |        -        |
| `GET /api/v1/orders/{id}`              | Свой |   +   |        -        |
| `PATCH /api/v1/orders/{id}/status`     |  -   |   +   |        -        |

---

### 14) Получение текущего пользователя: @AuthenticationPrincipal

В защищённых эндпоинтах часто нужно знать, кто сделал запрос. Spring Security предоставляет для этого `@AuthenticationPrincipal`:

```kotlin
@PostMapping("/api/v1/orders")
@PreAuthorize("hasRole('USER')")
fun createOrder(
    @Valid @RequestBody request: CreateOrderRequest,
    @AuthenticationPrincipal userDetails: UserDetails
): ResponseEntity<OrderResponse> {
    // userDetails.username содержит email текущего пользователя
    val order = orderService.create(request, userDetails.username)
    return ResponseEntity.status(HttpStatus.CREATED).body(order.toResponse())
}
```

Это позволяет привязать заказ к конкретному пользователю без необходимости передавать `userId` в теле запроса (что было бы небезопасно — клиент мог бы подставить чужой ID).

---

### 15) Обработка ошибок безопасности

Spring Security выбрасывает свои исключения, которые нужно обработать в `GlobalExceptionHandler`:

```kotlin
@ExceptionHandler(BadCredentialsException::class)
fun handleBadCredentials(e: BadCredentialsException): ResponseEntity<ErrorResponse> {
    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(ErrorResponse(401, "Неверный email или пароль"))
}

@ExceptionHandler(AccessDeniedException::class)
fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ErrorResponse> {
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(ErrorResponse(403, "Доступ запрещён"))
}
```

HTTP-статусы:
- `401 Unauthorized` — не аутентифицирован (нет токена, токен невалиден, неверные credentials).
- `403 Forbidden` — аутентифицирован, но нет прав (например, USER пытается создать ресторан).

> Важно: не возвращайте клиенту конкретику вроде "пользователь не найден" или "неверный пароль" — это помогает злоумышленнику подбирать credentials. Единое сообщение "Неверный email или пароль" безопаснее.

---

### 16) Обновление тестов

После добавления Spring Security существующие тесты сломаются — они не отправляют JWT. Есть два подхода:

**Подход 1: Spring Security Test**

Добавьте зависимость (она уже в `spring-boot-starter-test`):

```kotlin
@AutoConfigureMockMvc
@SpringBootTest
class RestaurantIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `создание ресторана от ADMIN возвращает 201`() {
        mockMvc.perform(
            post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Тест", "address": "Улица 1"}""")
        ).andExpect(status().isCreated)
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `создание ресторана от USER возвращает 403`() {
        mockMvc.perform(
            post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Тест", "address": "Улица 1"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `создание ресторана без токена возвращает 401`() {
        mockMvc.perform(
            post("/api/v1/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Тест", "address": "Улица 1"}""")
        ).andExpect(status().isUnauthorized)
    }
}
```

`@WithMockUser` — подставляет фиктивного пользователя с указанными ролями в `SecurityContext`, без реального JWT.

**Подход 2: получение реального токена в тесте**

```kotlin
fun getToken(role: String = "USER"): String {
    val registerRequest = RegisterRequest(
        email = "test-${UUID.randomUUID()}@test.com",
        password = "password123",
        name = "Test User"
    )
    val result = mockMvc.perform(
        post("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest))
    ).andReturn()

    return objectMapper.readTree(result.response.contentAsString)["token"].asText()
}

@Test
fun `создание заказа с валидным токеном`() {
    val token = getToken()
    mockMvc.perform(
        post("/api/v1/orders")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"dishIds": [1, 2]}""")
    ).andExpect(status().isCreated)
}
```

---

## Практическое задание

### 1) Расширьте сущность пользователя и добавьте миграцию

Сущность `User` в проекте уже есть — её нужно **дополнить**, а не создавать с нуля.

1. Добавьте в `UserEntity` поля `password` и `role` (или связь с таблицей ролей — см. раздел 5 теории).
2. Напишите Flyway-миграцию для добавления новых колонок (или таблицы ролей) в существующую таблицу `users`.
3. Добавьте в `UserRepository` методы `findByEmail` и `existsByEmail`.

### 2) Реализуйте регистрацию и логин

1. `POST /auth/register` — принимает email, пароль, имя. Возвращает JWT.
2. `POST /auth/login` — принимает email и пароль. Возвращает JWT.
3. Пароль хешируется через `BCryptPasswordEncoder` при сохранении.
4. Добавьте валидацию на DTO (email, минимальная длина пароля).
5. Регистрация с существующим email возвращает `409 Conflict`.

### 3) Реализуйте JWT-аутентификацию

1. `JwtService` — генерация, валидация токена, извлечение email и роли.
2. `JwtAuthenticationFilter` — извлекает токен из `Authorization: Bearer <token>`, валидирует и устанавливает аутентификацию.
3. Параметры JWT (`secret`, `expiration`) вынесены в `application.yaml`.

### 4) Настройте SecurityFilterChain

1. CSRF отключен, session policy = `STATELESS`.
2. `/auth/**` открыт для всех.
3. `GET`-запросы на просмотр ресторанов и меню — открыты для всех.
4. Остальные запросы — только для аутентифицированных пользователей.
5. JWT-фильтр подключен в цепочку.

### 5) Реализуйте авторизацию по ролям

1. Управление ресторанами и блюдами (`POST`, `PUT`, `DELETE`) — только `ADMIN`.
2. Создание заказа — только `USER` (или `ADMIN`).
3. Просмотр заказа — только владелец заказа или `ADMIN`.
4. Изменение статуса заказа — только `ADMIN`.
5. Используйте `@PreAuthorize` для декларативной защиты.

### 6) Обновите обработку ошибок

1. `BadCredentialsException` → `401 Unauthorized`.
2. `AccessDeniedException` → `403 Forbidden`.
3. Невалидный или просроченный JWT → `401 Unauthorized`.
4. Все ответы об ошибках — в едином формате `ErrorResponse`.

### 7) Обновите существующие тесты

1. Существующие интеграционные тесты должны учитывать авторизацию (`@WithMockUser` или реальный токен).
2. Добавьте тесты на авторизацию: запрос без токена, запрос с ролью `USER` на ADMIN-эндпоинт, запрос с невалидным токеном.

---

## Критерии оценки (максимум 25 баллов)

| Категория | Критерий | Баллы |
|:--|:--|:--:|
| Штраф | Не проходят тесты из ЛР-6 (с учётом авторизации) | -5 |
| Сущность и миграция | `UserEntity` расширен полями `password`/`role`, Flyway-миграция | 2 |
| Регистрация | Эндпоинт работает, пароль хешируется BCrypt, JWT возвращается | 3 |
| Логин | Эндпоинт работает, неверные credentials → 401, JWT возвращается | 3 |
| JwtService | Генерация, валидация, извлечение claims, конфигурация через `${ENV}` | 3 |
| JwtAuthenticationFilter | Фильтр парсит Bearer-токен, устанавливает аутентификацию | 2 |
| SecurityFilterChain | CSRF отключен, stateless, публичные/закрытые URL, фильтр подключен | 2 |
| Авторизация по ролям | `@PreAuthorize`, матрица доступа соблюдена | 3 |
| Владелец заказа | Просмотр заказа только владельцем или ADMIN | 2 |
| Обработка ошибок | 401/403 в едином формате, безопасные сообщения | 2 |
| Тесты на безопасность | Тесты на 401, 403, успешный доступ с ролью | 2 |
| Качество решения | Чистота кода, конфигурация, отсутствие захардкоженных секретов | 1 |
| **Итого** | | **25** |

---

## Мини-чеклист перед сдачей

1. `POST /auth/register` с валидными данными возвращает `201` и JWT.
2. `POST /auth/register` с дублирующимся email возвращает `409`.
3. `POST /auth/login` с верными credentials возвращает `200` и JWT.
4. `POST /auth/login` с неверным паролем возвращает `401`.
5. `GET /api/v1/restaurants` работает без токена.
6. `POST /api/v1/restaurants` без токена возвращает `401`.
7. `POST /api/v1/restaurants` с токеном `USER` возвращает `403`.
8. `POST /api/v1/restaurants` с токеном `ADMIN` возвращает `201`.
9. `POST /api/v1/orders` с токеном `USER` создаёт заказ, привязанный к текущему пользователю.
10. Все прежние тесты из ЛР-6 проходят (с учётом авторизации).
11. Пароли в БД хранятся в виде BCrypt-хеша, не в открытом виде.

---

## Что почитать

1. [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
2. [Spring Security with JWT — Baeldung](https://www.baeldung.com/security-spring)
3. [JWT Introduction — jwt.io](https://jwt.io/introduction)
4. [jjwt Library](https://github.com/jwtk/jjwt)
5. [Spring Security Kotlin DSL](https://docs.spring.io/spring-security/reference/servlet/configuration/kotlin.html)
6. [Testing with @WithMockUser](https://docs.spring.io/spring-security/reference/servlet/test/method.html)
