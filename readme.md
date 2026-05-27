# Лабораторная работа №13

## Мониторинг: Actuator, Prometheus, Grafana, Loki

---

## Цель работы

Подключить к сервису доставки еды observability-стек: экспортировать метрики приложения и контейнеров через Prometheus, визуализировать их в Grafana, агрегировать логи всех контейнеров в Loki и научиться их читать.

---

## Что нужно сдать

Ссылку на PR в ваш репозиторий (шаблон у вас есть).

---

## Теоретический блок

### 1) Зачем нужен мониторинг

Когда приложение работает в production, `docker stats` и `docker logs` — не вариант. Контейнеров несколько, история не сохраняется, поиск по логам ручной. Observability строится на трёх столпах:

- **Metrics** — числовые ряды во времени: RPS, задержки, потребление CPU/RAM. Позволяют отвечать на вопрос «что происходит?»
- **Logs** — структурированные события: ошибки, запросы, результаты операций. Отвечают на «почему так происходит?»
- **Traces** — цепочки вызовов через сервисы (в этой лабе не рассматриваем).

Цель — собирать метрики и логи **всех контейнеров** в одном месте и смотреть их через единый UI.

---

### 2) Spring Boot Actuator и Micrometer

**Spring Boot Actuator** предоставляет HTTP-эндпоинты для наблюдения за приложением: `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`.

**Micrometer** — это фасад над системами метрик (аналог SLF4J, но для метрик). Он абстрагирует код приложения от конкретного backend-а (Prometheus, Datadog, InfluxDB…). В Spring Boot приложение работает с `MeterRegistry`, а Micrometer сам форматирует данные под нужный backend.

Есть три основных типа метрик:

| Тип | Что измеряет | Пример |
|:--|:--|:--|
| **Counter** | Монотонно растущий счётчик | количество созданных заказов |
| **Timer** | Количество вызовов + суммарное время | длительность отправки email |
| **Gauge** | Мгновенное значение (может уменьшаться) | число сообщений в обработке |

Эндпоинт `/actuator/prometheus` экспортирует все метрики в текстовом формате Prometheus — его Prometheus сервер будет периодически «стягивать» (pull-модель).

---

### 3) Prometheus

**Prometheus** — база данных временных рядов (TSDB) с pull-моделью сбора метрик. Он по расписанию опрашивает (scrape) указанные цели, сохраняет данные и предоставляет язык запросов **PromQL**.

Основные PromQL-операторы:

```
# Мгновенное значение метрики
container_memory_usage_bytes{name="food-delivery-app"}

# Скорость изменения за 1 минуту (для Counter)
rate(food_orders_created_total[1m])

# Суммировать по тегу status
sum by (status) (food_orders_created_total)

# 95-й перцентиль задержки HTTP
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
```

UI Prometheus доступен на `http://localhost:9090`.

---

### 4) cAdvisor

**cAdvisor** (Container Advisor) — экспортер метрик Docker-контейнеров от Google. Он читает данные из `/sys` и Docker socket и публикует метрики по каждому контейнеру: CPU, RAM, сетевой трафик, I/O диска. Prometheus scrape-ит cAdvisor так же, как и приложение.

Полезные метрики cAdvisor:

```
container_cpu_usage_seconds_total          # CPU-время
container_memory_usage_bytes               # RSS + кеш
container_network_receive_bytes_total      # входящий трафик
container_network_transmit_bytes_total     # исходящий трафик
```

---

### 5) Grafana

**Grafana** — UI для визуализации метрик и логов. Она умеет подключаться к любым datasource (Prometheus, Loki, ClickHouse…) и строить дашборды. Для воспроизводимой настройки Grafana поддерживает **provisioning**: datasources и дашборды описываются в YAML/JSON-файлах и монтируются в контейнер — при старте Grafana их подхватывает автоматически.

На [grafana.com/grafana/dashboards](https://grafana.com/grafana/dashboards) есть тысячи готовых дашбордов. Достаточно скачать JSON и положить в папку provisioning.

---

### 6) Loki и Grafana Alloy

**Loki** — система агрегации логов от Grafana Labs. В отличие от ELK, Loki не индексирует текст целиком: индексируются только **лейблы** (container, service, level…), а сами строки хранятся сжатыми. Это дёшево и быстро. Запросы пишутся на языке **LogQL**:

```
# Все логи контейнера postgres
{compose_service="postgres"}

# Только ошибки из приложения
{compose_service="app"} |= "ERROR"

# JSON-парсинг и фильтрация по полю
{compose_service="app"} | json | level = "error"

# Скорость появления ошибок
rate({compose_service="app"} |= "ERROR" [5m])
```

**Grafana Alloy** — агент для сбора данных (замена Promtail, который перешёл в maintenance-mode). Alloy конфигурируется на языке **River** (`.alloy` файлы) и умеет собирать логи Docker-контейнеров через `/var/run/docker.sock` — без изменений в приложении или `logback`. Он автоматически обнаруживает все контейнеры, навешивает лейблы `container`, `compose_service` и шипит логи в Loki.

---

## Пошаговая инструкция

### Шаг 1. Actuator и Prometheus registry

Добавьте зависимости. Maven:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Gradle (`build.gradle.kts`):

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("io.micrometer:micrometer-registry-prometheus")
```

Настройте `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
  info:
    env:
      enabled: true

info:
  app:
    name: food-delivery
    version: '@project.version@'
```

Проверьте:

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP","components":{...}}

curl http://localhost:8080/actuator/prometheus | head -20
# → # HELP jvm_memory_used_bytes ...
```

---

### Шаг 2. Компонент метрик и четыре кастомных измерения

Вместо того чтобы разбрасывать `MeterRegistry` по всем сервисам, сделаем один выделенный компонент — `OrderMetrics`. Он регистрирует все метрики при старте и предоставляет удобный API для замеров. Сервисы инжектят `OrderMetrics`, а не `MeterRegistry` напрямую.

#### Метрики, которые нужно реализовать

| Имя | Тип | Смысл |
|:--|:--|:--|
| `order_processing_duration_seconds` | Timer | Полное время обработки запроса создания заказа |
| `orders_created_total` | Counter | Количество успешно созданных заказов |
| `business_errors_total` | Counter (tag `type`) | Бизнес-ошибки: `validation`, `payment` |
| `order_creation_failed_total` | Counter (tag `reason`) | Отказы создания: `stock_empty` |

#### Компонент OrderMetrics

```kotlin
@Component
class OrderMetrics(private val meterRegistry: MeterRegistry) {

    // Timer — измеряет длительность и строит перцентильную гистограмму
    val processingDuration: Timer = Timer.builder("order_processing_duration_seconds")
        .description("Длительность обработки запроса на создание заказа")
        .publishPercentileHistogram()
        .register(meterRegistry)

    // Counter — монотонно растёт при каждом успешном создании
    val ordersCreated: Counter = Counter.builder("orders_created_total")
        .description("Количество успешно созданных заказов")
        .register(meterRegistry)

    // Для счётчиков с тегами удобнее использовать методы-обёртки:
    // Micrometer кеширует Counter по имени+тегам, повторный вызов не создаёт новый объект
    fun recordBusinessError(type: String) =
        meterRegistry.counter("business_errors_total", "type", type).increment()

    fun recordOrderCreationFailed(reason: String) =
        meterRegistry.counter("order_creation_failed_total", "reason", reason).increment()
}
```

#### Использование в OrderService

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val dishRepository: DishRepository,
    private val eventPublisher: OrderEventPublisher,
    private val metrics: OrderMetrics   // ← инжектим OrderMetrics, не MeterRegistry
) {
    fun createOrder(request: CreateOrderRequest, userId: Long): Order {
        return metrics.processingDuration.record {
            // Валидация входных данных
            if (request.dishIds.isEmpty()) {
                metrics.recordBusinessError("validation")
                throw ValidationException("Список блюд не может быть пустым")
            }

            // Проверка наличия блюд
            val dishes = dishRepository.findAllById(request.dishIds)
            if (dishes.any { !it.available }) {
                metrics.recordOrderCreationFailed("stock_empty")
                throw OrderCreationException("Одно или несколько блюд недоступны")
            }

            // Создание и сохранение заказа
            val saved = orderRepository.save(/* ... */)
            metrics.ordersCreated.increment()

            eventPublisher.publishOrderCreated(/* ... */)
            saved
        }!!
    }
}
```

> `Timer.record {}` возвращает `T?` — отсюда `!!`. Альтернатива: `recordCallable { ... }`.

Ошибки оплаты перехватываются там, где происходит вызов платёжного сервиса — аналогично: `metrics.recordBusinessError("payment")`.

Проверьте после запуска:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep -E "^(order|business)"
# → order_processing_duration_seconds_count 0.0
# → order_processing_duration_seconds_sum 0.0
# → orders_created_total 0.0
# → business_errors_total{type="validation"} 0.0
# → order_creation_failed_total{reason="stock_empty"} 0.0
```

---

### Шаг 3. Структура файлов мониторинга

Создайте директорию `monitoring/` рядом с `docker-compose.yml`:

```
your-project/
├── docker-compose.yml
├── monitoring/
│   ├── prometheus.yml
│   ├── loki-config.yaml
│   ├── alloy-config.alloy
│   └── grafana/
│       ├── provisioning/
│       │   ├── datasources/
│       │   │   └── datasources.yaml
│       │   └── dashboards/
│       │       └── dashboards.yaml
│       └── dashboards/
│           ├── cadvisor.json       ← скачать на шаге 7
│           ├── jvm.json            ← скачать на шаге 7
│           └── business.json       ← создать и экспортировать на шаге 8
```

---

### Шаг 4. Prometheus

Создайте `monitoring/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'spring'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app:8080']

  - job_name: 'cadvisor'
    static_configs:
      - targets: ['cadvisor:8080']
```

> `app` — имя сервиса вашего Spring Boot приложения в `docker-compose.yml`. Если у вас другое имя — замените.

---

### Шаг 5. Loki

Создайте `monitoring/loki-config.yaml`:

```yaml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096
  log_level: warn

common:
  instance_addr: 127.0.0.1
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2020-10-24
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

limits_config:
  reject_old_samples: true
  reject_old_samples_max_age: 168h
  ingestion_rate_mb: 4
  ingestion_burst_size_mb: 6
```

---

### Шаг 6. Grafana Alloy

Создайте `monitoring/alloy-config.alloy`:

```alloy
// Обнаружение всех Docker-контейнеров
discovery.docker "all_containers" {
  host = "unix:///var/run/docker.sock"
}

// Добавляем лейблы: имя контейнера и имя compose-сервиса
discovery.relabel "docker_labels" {
  targets = discovery.docker.all_containers.targets

  rule {
    source_labels = ["__meta_docker_container_name"]
    regex         = "/(.*)"
    target_label  = "container"
  }

  rule {
    source_labels = ["__meta_docker_container_label_com_docker_compose_service"]
    target_label  = "compose_service"
  }

  rule {
    source_labels = ["__meta_docker_container_label_com_docker_compose_project"]
    target_label  = "compose_project"
  }
}

// Читаем stdout/stderr контейнеров через Docker socket
loki.source.docker "containers" {
  host             = "unix:///var/run/docker.sock"
  targets          = discovery.relabel.docker_labels.output
  forward_to       = [loki.write.loki_push.receiver]
  refresh_interval = "5s"
}

// Отправляем логи в Loki
loki.write "loki_push" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }
}
```

---

### Шаг 7. Grafana: datasources и provisioning

Создайте `monitoring/grafana/provisioning/datasources/datasources.yaml`:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    editable: false
```

Создайте `monitoring/grafana/provisioning/dashboards/dashboards.yaml`:

```yaml
apiVersion: 1

providers:
  - name: Default
    type: file
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards
```

#### Скачайте готовые дашборды

Для cAdvisor перейдите по ссылке и нажмите **Download JSON**:

```
https://grafana.com/grafana/dashboards/14282
```

Сохраните файл как `monitoring/grafana/dashboards/cadvisor.json`.

Для JVM/Micrometer:

```
https://grafana.com/grafana/dashboards/4701
```

Сохраните как `monitoring/grafana/dashboards/jvm.json`.

---

### Шаг 8. Docker Compose: добавьте 5 сервисов

Дополните `docker-compose.yml`:

```yaml
services:
  # ... ваши существующие сервисы ...

  prometheus:
    image: prom/prometheus:v2.55.0
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=15d'
    restart: unless-stopped

  cadvisor:
    image: gcr.io/cadvisor/cadvisor:v0.49.1
    ports:
      - "8088:8080"
    volumes:
      - /:/rootfs:ro
      - /var/run:/var/run:ro
      - /sys:/sys:ro
      - /var/lib/docker/:/var/lib/docker:ro
      - /dev/disk/:/dev/disk:ro
    privileged: true
    restart: unless-stopped

  grafana:
    image: grafana/grafana:11.4.0
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD:-admin}
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./monitoring/grafana/dashboards:/var/lib/grafana/dashboards:ro
    restart: unless-stopped

  loki:
    image: grafana/loki:3.3.0
    ports:
      - "3100:3100"
    volumes:
      - ./monitoring/loki-config.yaml:/etc/loki/local-config.yaml:ro
      - loki_data:/loki
    command: -config.file=/etc/loki/local-config.yaml
    restart: unless-stopped

  alloy:
    image: grafana/alloy:v1.5.0
    ports:
      - "12345:12345"
    volumes:
      - ./monitoring/alloy-config.alloy:/etc/alloy/config.alloy:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro
    command: run --server.http.listen-addr=0.0.0.0:12345 /etc/alloy/config.alloy
    depends_on:
      - loki
    restart: unless-stopped

volumes:
  # ... ваши существующие volumes ...
  prometheus_data:
  grafana_data:
  loki_data:
```

> **Важно:** cAdvisor требует `privileged: true` и монтирования `/sys`, `/var/lib/docker`. На macOS вместо `/var/lib/docker` используется `/var/lib/docker.raw` — уточните, если запускаете на Mac.

---

### Шаг 9. Запуск и проверка

```bash
docker compose up -d
```

**Prometheus.** Откройте `http://localhost:9090/targets`. Оба job (`spring` и `cadvisor`) должны быть в состоянии `UP`.

Если `spring` в состоянии `DOWN` — проверьте имя сервиса приложения в `prometheus.yml` и что приложение действительно поднялось.

Проверьте PromQL-запросы:

```
# CPU всех контейнеров
sum by (name) (rate(container_cpu_usage_seconds_total{name!=""}[1m]))

# Счётчик созданных заказов
orders_created_total

# Бизнес-ошибки по типу
sum by (type) (business_errors_total)

# RPS приложения
rate(http_server_requests_seconds_count[1m])
```

**Grafana.** Откройте `http://localhost:3000`, войдите (admin / admin или ваш пароль из env). В меню **Dashboards** должны появиться два дашборда: cAdvisor и JVM Micrometer.

Проверьте, что панели рисуют данные: создайте несколько заказов через API и подождите 15–30 секунд (scrape interval).

**Loki.** В Grafana откройте **Explore** → выберите datasource **Loki**. Введите запросы:

```
{compose_service="postgres"}
{compose_service="redis"}
{compose_service="rabbitmq"}
{compose_service="app"}
```

В каждом должны идти логи. Убедитесь, что Alloy читает логи всех контейнеров.

---

### Шаг 10. Кастомный business-дашборд

В Grafana создайте новый дашборд (**+ → New dashboard**) и добавьте четыре панели.

**Панель 1: Заказы в минуту**

- Тип: **Time series**
- PromQL: `rate(orders_created_total[1m])`
- Название: «Заказы в минуту»

**Панель 2: P95 длительности создания заказа**

- Тип: **Time series**
- PromQL: `histogram_quantile(0.95, rate(order_processing_duration_seconds_bucket[5m]))`
- Название: «P95 обработки заказа (сек)»

**Панель 3: Бизнес-ошибки по типу**

- Тип: **Time series**
- PromQL: `sum by (type) (rate(business_errors_total[5m]))`
- Название: «Бизнес-ошибки (validation / payment)»

**Панель 4: Отказы создания заказа**

- Тип: **Stat**
- PromQL: `sum by (reason) (increase(order_creation_failed_total[1h]))`
- Название: «Отказы создания (последний час)»

Сохраните дашборд. Нажмите **Share → Export → Save to file**. Сохраните файл как `monitoring/grafana/dashboards/business.json`.

---

### ⭐ Бонус (+10 баллов): метрики через AOP

В базовом решении `OrderService` знает об `OrderMetrics` — это компромисс. Чище было бы вынести замер метрик в сквозную (cross-cutting) логику с помощью **аспектно-ориентированного программирования**. Тогда сервис остаётся чистым, а измерения навешиваются снаружи.

Добавьте зависимость (если ещё нет):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

---

#### Вариант A: готовая аннотация `@Timed` от Micrometer

Самый быстрый способ — использовать встроенную аннотацию Micrometer. Нужно только зарегистрировать `TimedAspect` как бин:

```kotlin
@Configuration
class MetricsConfig {
    @Bean
    fun timedAspect(registry: MeterRegistry): TimedAspect = TimedAspect(registry)
}
```

Затем пометить метод:

```kotlin
@Timed(
    value = "order_processing_duration_seconds",
    description = "Длительность обработки заказа",
    percentiles = [0.5, 0.95, 0.99]
)
fun createOrder(request: CreateOrderRequest, userId: Long): Order {
    // ...
}
```

`TimedAspect` автоматически добавит тег `exception` — при успехе `"none"`, при исключении — имя класса. В Prometheus появятся ряды вида:

```
order_processing_duration_seconds_bucket{exception="none", ...}
order_processing_duration_seconds_bucket{exception="ValidationException", ...}
```

**Ограничения:** нет контроля над тегами при конкретных исключениях, нельзя инкрементировать дополнительные счётчики (`ordersCreated`, `businessErrors`) из того же аспекта.

---

#### Вариант B: кастомная аннотация с именем метрики и тегами

Для полного контроля — собственная аннотация с параметром `metricName` и аспект, который добавляет теги в зависимости от исхода.

**Аннотация:**

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TrackOrderProcessing(
    val metricName: String = "order_processing_duration_seconds"
)
```

**Аспект:**

Вместо `Timer.record {}` используем `Timer.Sample` — он позволяет остановить таймер с разными наборами тегов в зависимости от исхода:

```kotlin
@Aspect
@Component
class OrderMetricsAspect(
    private val metrics: OrderMetrics,
    private val meterRegistry: MeterRegistry
) {

    @Around("@annotation(track)")
    fun around(joinPoint: ProceedingJoinPoint, track: TrackOrderProcessing): Any {
        val sample = Timer.start(meterRegistry)

        return try {
            val result = joinPoint.proceed()

            sample.stop(
                Timer.builder(track.metricName)
                    .tag("outcome", "success")
                    .publishPercentileHistogram()
                    .register(meterRegistry)
            )
            metrics.ordersCreated.increment()
            result

        } catch (e: ValidationException) {
            sample.stop(
                Timer.builder(track.metricName)
                    .tag("outcome", "error")
                    .tag("reason", "validation")
                    .publishPercentileHistogram()
                    .register(meterRegistry)
            )
            metrics.recordBusinessError("validation")
            throw e

        } catch (e: PaymentException) {
            sample.stop(
                Timer.builder(track.metricName)
                    .tag("outcome", "error")
                    .tag("reason", "payment")
                    .publishPercentileHistogram()
                    .register(meterRegistry)
            )
            metrics.recordBusinessError("payment")
            throw e

        } catch (e: OrderCreationException) {
            sample.stop(
                Timer.builder(track.metricName)
                    .tag("outcome", "error")
                    .tag("reason", e.reason)   // reason берём из поля исключения
                    .publishPercentileHistogram()
                    .register(meterRegistry)
            )
            metrics.recordOrderCreationFailed(e.reason)
            throw e
        }
    }
}
```

**Использование в сервисе** — метод помечается аннотацией, имя метрики задаётся явно:

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val dishRepository: DishRepository,
    private val eventPublisher: OrderEventPublisher
    // OrderMetrics больше не нужен
) {
    @TrackOrderProcessing(metricName = "order_processing_duration_seconds")
    fun createOrder(request: CreateOrderRequest, userId: Long): Order {
        // только чистая бизнес-логика
        if (request.dishIds.isEmpty())
            throw ValidationException("Список блюд не может быть пустым")

        val dishes = dishRepository.findAllById(request.dishIds)
        if (dishes.any { !it.available })
            throw OrderCreationException(reason = "stock_empty")

        val saved = orderRepository.save(/* ... */)
        eventPublisher.publishOrderCreated(/* ... */)
        return saved
    }
}
```

Теперь в Prometheus появятся отдельные ряды по каждому исходу — можно строить PromQL с фильтрацией по тегу:

```
# P95 только успешных запросов
histogram_quantile(0.95,
  rate(order_processing_duration_seconds_bucket{outcome="success"}[5m])
)

# P95 запросов, упавших с ошибкой валидации
histogram_quantile(0.95,
  rate(order_processing_duration_seconds_bucket{outcome="error", reason="validation"}[5m])
)
```

> `Timer.start()` фиксирует момент начала. `sample.stop(timer)` фиксирует конец и записывает длительность в переданный таймер. Один `sample` можно остановить только один раз — отсюда структура try/catch вместо finally.

> Аспект перехватывает любой метод, помеченный `@TrackOrderProcessing`, независимо от сервиса. Появится второй flow — достаточно добавить аннотацию с нужным именем метрики.

---

## Критерии оценки (максимум 15 баллов + 10 бонусных)

| Категория | Критерий | Баллы |
|:--|:--|:--:|
| Штраф | Не проходят тесты из предыдущих ЛР | −5 |
| Actuator | `/actuator/prometheus` отдаёт метрики, health/probes включены | 2 |
| OrderMetrics | Компонент создан, все 4 метрики зарегистрированы | 2 |
| Замеры | Метрики инкрементируются/измеряются в правильных местах | 2 |
| Prometheus | Поднят в compose, оба scrape target в состоянии `UP` | 2 |
| Grafana | Datasources Prometheus и Loki провижионятся автоматически | 1 |
| Дашборды | Дашборды cAdvisor и JVM видны и рисуют данные | 2 |
| Business-дашборд | 4 панели по кастомным метрикам, JSON закоммичен | 2 |
| Loki + Alloy | Логи всех контейнеров видны в Grafana Explore | 2 |
| **Итого** | | **15** |
| ⭐ Бонус: AOP | Метрики замеряются через аспект, сервис не зависит от `OrderMetrics` | +10 |
| **Максимум с бонусом** | | **25** |

---

## Мини-чеклист перед сдачей

1. `docker compose up -d` поднимает весь стек без ошибок.
2. `http://localhost:9090/targets` — `spring` и `cadvisor` в состоянии `UP`.
3. `curl localhost:8080/actuator/prometheus | grep -E "^(order|business)"` — видны все 4 кастомные метрики.
4. `http://localhost:3000` → Dashboards → два импортированных дашборда рисуют данные.
5. Business-дашборд содержит четыре панели и его JSON лежит в `monitoring/grafana/dashboards/business.json`.
6. Grafana → Explore → Loki → запросы по `compose_service` возвращают логи для `postgres`, `redis`, `rabbitmq`, `app`.
7. В репозитории закоммичена директория `monitoring/` целиком.
8. Все тесты из предыдущих ЛР проходят.

---

## Что почитать

1. [Spring Boot Actuator — Reference](https://docs.spring.io/spring-boot/reference/actuator/index.html)
2. [Micrometer Documentation](https://micrometer.io/docs)
3. [Prometheus Getting Started](https://prometheus.io/docs/prometheus/latest/getting_started/)
4. [cAdvisor на GitHub](https://github.com/google/cadvisor)
5. [Grafana Provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
6. [Loki Getting Started](https://grafana.com/docs/loki/latest/get-started/)
7. [Grafana Alloy — loki.source.docker](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.source.docker/)
8. [Spring AOP — Reference](https://docs.spring.io/spring-framework/reference/core/aop.html)
9. [Aspect Oriented Programming with Kotlin — Baeldung](https://www.baeldung.com/kotlin/spring-aop)
