# Лабораторная работа №11

## Брокер сообщений: RabbitMQ

---

## Цель работы

Ввести в проект доставки еды событийную архитектуру: подключить RabbitMQ, публиковать доменные события при изменении
заказов и перевести отправку email-уведомлений из ЛР-10 с прямого вызова на асинхронную обработку через очередь.

---

## Что нужно сдать

Ссылку на PR в ваш репозиторий (шаблон у вас есть).

---

## Теоретический блок

### 1) Проблема: тесная связность

В ЛР-10 `OrderService` при смене статуса заказа напрямую вызывает `NotificationService.sendEmail()`. Это работает, но
создаёт проблемы:

```kotlin
// Сейчас: OrderService знает про NotificationService
fun updateStatus(id: Long, newStatus: OrderStatus): Order {
    val order = findOrder(id)
    order.status = newStatus
    val saved = orderRepository.save(order)
    notificationService.sendEmail(saved)  // ← прямая зависимость
    return saved
}
```

**Что плохо:**

- `OrderService` знает про `NotificationService` — нарушение принципа единственной ответственности.
- Если отправка email упадёт — транзакция откатится или заказ вернётся с ошибкой, хотя статус уже поменялся.
- Чтобы добавить новый реакцию на событие (SMS, push, аналитика) — нужно лезть в `OrderService`.
- Под нагрузкой медленная почта блокирует поток HTTP-запроса.

Решение — **разделить факт события и его обработку**: `OrderService` публикует событие "статус изменился" и больше ни о
чём не знает. Кто и как реагирует — не его дело.

---

### 2) Брокер сообщений: концепция

**Брокер сообщений** — промежуточное звено между producer (тот, кто публикует события) и consumer (тот, кто их
обрабатывает). Брокер принимает сообщения, хранит их и доставляет подписчикам.

```
OrderService           RabbitMQ              NotificationService
  (producer)           (broker)                  (consumer)

order.updateStatus()
       │
       ▼
  publish event  ──→  [queue]  ──→  @RabbitListener
                                         │
                                         ▼
                                    sendEmail()
```

**Что это даёт:**

- **Decoupling** — producer и consumer не знают друг о друге и не зависят от доступности друг друга.
- **Надёжность** — если consumer упал, сообщения накапливаются в очереди и будут обработаны после его восстановления.
- **Масштабируемость** — можно запустить несколько экземпляров consumer-а, RabbitMQ распределит нагрузку.
- **Расширяемость** — новая реакция на событие = новый consumer, без изменения producer-а.

---

### 3) Ключевые понятия AMQP

RabbitMQ реализует протокол **AMQP** (Advanced Message Queuing Protocol). Важно понять четыре сущности и как они
связаны:

```
Producer  →  Exchange  →  Queue  →  Consumer
               ↑
           Binding (routing key)
```

- **Producer** — публикует сообщение в **exchange**, не в очередь напрямую.
- **Exchange** — получает сообщение и маршрутизирует его в одну или несколько очередей по правилам.
- **Queue** — буфер, где сообщения хранятся до получения consumer-ом.
- **Binding** — правило связи exchange → queue. Определяет, какие сообщения попадают в какую очередь.
- **Routing key** — метка сообщения, по которой exchange решает, куда его направить.
- **Consumer** — подписывается на очередь и обрабатывает сообщения.

#### Типы exchange

| Тип         | Маршрутизация                          | Когда использовать           |
|:------------|:---------------------------------------|:-----------------------------|
| **Direct**  | По точному совпадению routing key      | Одно событие → одна очередь  |
| **Topic**   | По паттерну (`order.*`, `*.created`)   | Гибкая фильтрация событий    |
| **Fanout**  | Во все привязанные очереди (broadcast) | Уведомить всех подписчиков   |
| **Headers** | По заголовкам сообщения                | Редко, сложная маршрутизация |

Для сервиса доставки удобнее всего **topic exchange**: routing key вида `order.created`, `order.status.changed` — и
любой consumer подписывается на интересующий паттерн.

---

### 4) RabbitMQ в docker-compose

RabbitMQ поставляется с веб-интерфейсом управления (Management UI) — образ с тегом `-management`:

```yaml
services:
  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"    # AMQP — подключение приложения
      - "15672:15672"  # Management UI — браузер
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:guest}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASS:guest}
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: [ "CMD", "rabbitmq-diagnostics", "ping" ]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  rabbitmq_data:
```

Management UI доступен по адресу `http://localhost:15672`. Там можно смотреть очереди, сообщения, consumer-ов и вручную
публиковать тестовые сообщения.

---

### 5) Spring AMQP: зависимости и конфигурация

```xml

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

Настройки в `application.yaml`:

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASS:guest}
```

#### Объявление инфраструктуры через конфигурацию

Exchange, queue и binding лучше объявлять через Spring-бины — тогда они создаются автоматически при старте, даже если их
нет в брокере:

```kotlin
@Configuration
class RabbitConfig {

    companion object {
        const val EXCHANGE = "order.exchange"
        const val ORDER_STATUS_QUEUE = "order.status.changed.queue"
        const val ORDER_STATUS_ROUTING_KEY = "order.status.changed"
        const val ORDER_CREATED_QUEUE = "order.created.queue"
        const val ORDER_CREATED_ROUTING_KEY = "order.created"
    }

    @Bean
    fun orderExchange(): TopicExchange =
        TopicExchange(EXCHANGE)

    @Bean
    fun orderStatusQueue(): Queue =
        QueueBuilder.durable(ORDER_STATUS_QUEUE).build()

    @Bean
    fun orderCreatedQueue(): Queue =
        QueueBuilder.durable(ORDER_CREATED_QUEUE).build()

    @Bean
    fun orderStatusBinding(): Binding =
        BindingBuilder
            .bind(orderStatusQueue())
            .to(orderExchange())
            .with(ORDER_STATUS_ROUTING_KEY)

    @Bean
    fun orderCreatedBinding(): Binding =
        BindingBuilder
            .bind(orderCreatedQueue())
            .to(orderExchange())
            .with(ORDER_CREATED_ROUTING_KEY)

    @Bean
    fun messageConverter(): MessageConverter =
        Jackson2JsonMessageConverter()

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        messageConverter: MessageConverter
    ): RabbitTemplate = RabbitTemplate(connectionFactory).apply {
        this.messageConverter = messageConverter
    }
}
```

`Jackson2JsonMessageConverter` — сериализация сообщений в JSON. Без него Spring AMQP использует Java Serialization,
которую сложно читать и которая требует `Serializable` на классах событий.

`QueueBuilder.durable()` — очередь переживает рестарт RabbitMQ. Без `durable` очередь создаётся заново при рестарте, и
все накопленные сообщения теряются.

---

### 6) Доменные события: что публиковать

Событие — это описание того, что **уже произошло**. Именуется в прошедшем времени:

```kotlin
data class OrderCreatedEvent(
    val orderId: Long,
    val userId: Long,
    val dishIds: List<Long>,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

data class OrderStatusChangedEvent(
    val orderId: Long,
    val userId: Long,
    val userEmail: String,
    val oldStatus: OrderStatus,
    val newStatus: OrderStatus,
    val changedAt: LocalDateTime = LocalDateTime.now()
)
```

Событие содержит всё, что consumer-у нужно для обработки — без дополнительных запросов в БД. `userEmail` в
`OrderStatusChangedEvent` нужен consumer-у для отправки письма.

> Не передавайте в событии JPA-сущности напрямую. Сущность привязана к Hibernate-сессии, которая закрывается после
> транзакции. Сериализованная сущность потянет за собой связи (lazy-loading вне сессии бросит исключение). Используйте
> отдельные data-классы для событий.

---

### 7) Публикация: RabbitTemplate

```kotlin
@Service
class OrderEventPublisher(
    private val rabbitTemplate: RabbitTemplate
) {
    private val logger = KotlinLogging.logger {}

    fun publishOrderCreated(event: OrderCreatedEvent) {
        rabbitTemplate.convertAndSend(
            RabbitConfig.EXCHANGE,
            RabbitConfig.ORDER_CREATED_ROUTING_KEY,
            event
        )
        logger.info { "Опубликовано событие OrderCreated для заказа ${event.orderId}" }
    }

    fun publishOrderStatusChanged(event: OrderStatusChangedEvent) {
        rabbitTemplate.convertAndSend(
            RabbitConfig.EXCHANGE,
            RabbitConfig.ORDER_STATUS_ROUTING_KEY,
            event
        )
        logger.info { "Опубликовано событие OrderStatusChanged: ${event.oldStatus} → ${event.newStatus}" }
    }
}
```

Теперь `OrderService` зависит только от `OrderEventPublisher`, а не от `NotificationService`:

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val eventPublisher: OrderEventPublisher
) {
    fun updateStatus(id: Long, newStatus: OrderStatus): Order {
        val order = findOrder(id)
        val oldStatus = order.status
        order.status = newStatus
        val saved = orderRepository.save(order)

        eventPublisher.publishOrderStatusChanged(
            OrderStatusChangedEvent(
                orderId = saved.id,
                userId = saved.user.id,
                userEmail = saved.user.email,
                oldStatus = oldStatus,
                newStatus = newStatus
            )
        )
        return saved
    }
}
```

---

### 8) Потребление: @RabbitListener

```kotlin
@Component
class NotificationConsumer(
    private val notificationService: NotificationService
) {
    private val logger = KotlinLogging.logger {}

    @RabbitListener(queues = [RabbitConfig.ORDER_STATUS_QUEUE])
    fun handleOrderStatusChanged(event: OrderStatusChangedEvent) {
        logger.info { "Получено событие: заказ ${event.orderId} → ${event.newStatus}" }
        notificationService.sendStatusChangedEmail(event)
    }

    @RabbitListener(queues = [RabbitConfig.ORDER_CREATED_QUEUE])
    fun handleOrderCreated(event: OrderCreatedEvent) {
        logger.info { "Новый заказ ${event.orderId} от пользователя ${event.userId}" }
        // здесь может быть любая логика: аналитика, подтверждение, резервирование
    }
}
```

`@RabbitListener` поднимает фоновый поток, который постоянно слушает очередь. При получении сообщения Spring
автоматически десериализует JSON в нужный тип и вызывает метод.

#### Acknowledgements: явное подтверждение обработки

По умолчанию RabbitMQ считает сообщение обработанным сразу после доставки (auto-ack). Если consumer упадёт в процессе
обработки — сообщение потеряется. Надёжнее подтверждать вручную:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual
```

```kotlin
@RabbitListener(queues = [RabbitConfig.ORDER_STATUS_QUEUE])
fun handleOrderStatusChanged(
    event: OrderStatusChangedEvent,
    channel: Channel,
    @Header(AmqpHeaders.DELIVERY_TAG) deliveryTag: Long
) {
    try {
        notificationService.sendStatusChangedEmail(event)
        channel.basicAck(deliveryTag, false)   // подтвердить обработку
    } catch (e: Exception) {
        logger.error(e) { "Ошибка обработки события для заказа ${event.orderId}" }
        channel.basicNack(deliveryTag, false, false)  // отклонить, не возвращать в очередь
    }
}
```

`basicNack(..., requeue = false)` — отклонить сообщение без возврата в очередь. Если настроен DLQ, сообщение уйдёт туда.

---

### 9) Dead Letter Queue: обработка ошибок

Если consumer не справился с сообщением (бросил исключение, отклонил через `basicNack`) — куда деть это сообщение? Без
DLQ оно просто пропадёт. **Dead Letter Queue** — специальная очередь для "мёртвых" сообщений, которые не удалось
обработать.

```kotlin
@Bean
fun orderStatusQueue(): Queue =
    QueueBuilder.durable(ORDER_STATUS_QUEUE)
        .withArgument("x-dead-letter-exchange", "")           // default exchange
        .withArgument("x-dead-letter-routing-key", ORDER_STATUS_DLQ)
        .build()

@Bean
fun orderStatusDlq(): Queue =
    QueueBuilder.durable(ORDER_STATUS_DLQ).build()
```

Теперь при `basicNack(..., requeue = false)` сообщение автоматически переедет в `ORDER_STATUS_DLQ`. Оттуда его можно:

- Проанализировать вручную через Management UI.
- Обработать отдельным consumer-ом (например, залогировать и оповестить команду).
- Переотправить в основную очередь после исправления ошибки.

---

### 10) Идемпотентность consumer-а

Сеть ненадёжна: брокер может доставить одно сообщение дважды (при переподключении, ack-таймауте). Consumer должен *
*безопасно обработать дубль**:

```kotlin
@RabbitListener(queues = [RabbitConfig.ORDER_STATUS_QUEUE])
fun handleOrderStatusChanged(event: OrderStatusChangedEvent) {
    // Проверяем, не обрабатывали ли уже это событие
    if (processedEventRepository.existsByOrderIdAndStatus(
            event.orderId, event.newStatus
        )
    ) {
        logger.warn { "Дубль события для заказа ${event.orderId}, пропускаем" }
        return
    }

    notificationService.sendStatusChangedEmail(event)
    processedEventRepository.save(ProcessedEvent(event.orderId, event.newStatus))
}
```

Простейший способ — таблица `processed_events(order_id, new_status)` с уникальным индексом. Повторная попытка вставки
бросит исключение → дубль обнаружен.

---

## Практическое задание

### 1) Подключите RabbitMQ

1. Добавьте RabbitMQ в `docker-compose.yml` с healthcheck и Management UI.
2. Добавьте зависимость `spring-boot-starter-amqp`.
3. Вынесите параметры подключения в переменные окружения.

### 2) Опишите инфраструктуру в конфигурации

1. Создайте `RabbitConfig` с topic exchange, двумя очередями и двумя binding-ами.
2. Настройте `Jackson2JsonMessageConverter`.
3. Объявите durable-очереди.

### 3) Определите доменные события

Опишите события как отдельные data-классы. Подумайте, какие данные понадобятся consumer-у, чтобы обработать событие без
дополнительных запросов в БД.

### 4) Перейдите на событийную модель в OrderService

1. Уберите прямую зависимость `OrderService` → `NotificationService`.
2. При создании заказа — публикуйте событие через `RabbitTemplate`.
3. При смене статуса — публикуйте событие через `RabbitTemplate`.

### 5) Реализуйте consumer

1. Создайте `@RabbitListener` для события смены статуса — пусть он отправляет email через `NotificationService` из
   ЛР-10.
2. Создайте `@RabbitListener` для события создания заказа — логику определите сами (логирование, подтверждение,
   аналитика).

### 6) Настройте надёжность

1. Настройте DLQ для очереди смены статуса.
2. Добавьте базовую идемпотентность: если событие уже обрабатывалось — пропустить.

---

## Критерии оценки (максимум 15 баллов)

| Категория          | Критерий                                                                      | Баллы  |
|:-------------------|:------------------------------------------------------------------------------|:------:|
| Штраф              | Не проходят тесты из предыдущих ЛР                                            |   -5   |
| RabbitMQ в compose | Поднимается с healthcheck, Management UI доступен                             |   1    |
| Конфигурация       | Exchange, queues, bindings, JSON-конвертер, durable                           |   3    |
| Доменные события   | Отдельные классы событий, содержат нужные данные                              |   2    |
| Публикация         | OrderService публикует события, нет прямой зависимости на NotificationService |   3    |
| Consumer           | @RabbitListener обрабатывает оба события                                      |   3    |
| DLQ                | Dead Letter Queue настроен и работает                                         |   2    |
| Идемпотентность    | Дубль события не приводит к повторной обработке                               |   1    |
| **Итого**          |                                                                               | **15** |

---

## Мини-чеклист перед сдачей

1. `docker compose up` поднимает RabbitMQ, Management UI открывается на `http://localhost:15672`.
2. В Management UI видны exchange `order.exchange` и обе очереди.
3. Смена статуса заказа → в логах consumer-а появляется запись об обработке → email отправляется.
4. `OrderService` не импортирует `NotificationService`.
5. При остановке consumer-а сообщения накапливаются в очереди и обрабатываются после его запуска.
6. Повторная публикация того же события → consumer пропускает дубль (проверить по логам).
7. Все тесты из предыдущих ЛР проходят.

---

## Что почитать

1. [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)
2. [Spring AMQP Reference](https://docs.spring.io/spring-amqp/reference/)
3. [Spring Boot + RabbitMQ — Baeldung](https://www.baeldung.com/spring-amqp)
4. [Dead Letter Exchanges — RabbitMQ](https://www.rabbitmq.com/docs/dlx)
5. [Message Acknowledgements — RabbitMQ](https://www.rabbitmq.com/docs/confirms)
