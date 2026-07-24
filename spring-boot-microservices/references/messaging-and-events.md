# Messaging & Event-Driven Patterns

Asynchronous, event-driven communication is how services stay decoupled and
independently available. This covers the patterns that make it reliable — because
"just publish an event" hides several ways to lose or double-process data.

## Table of contents
- [Why events](#why-events)
- [Kafka vs RabbitMQ](#kafka-vs-rabbitmq)
- [Spring for Apache Kafka basics](#spring-for-apache-kafka-basics)
- [Event design](#event-design)
- [The dual-write problem & transactional outbox](#the-dual-write-problem--transactional-outbox)
- [Idempotent consumers](#idempotent-consumers)
- [Error handling: retries & dead-letter](#error-handling-retries--dead-letter)
- [Schema evolution](#schema-evolution)

## Why events

Publishing a domain event ("OrderPlaced") lets any number of services react without
the publisher knowing or waiting for them. This removes temporal coupling (consumers
can be down without breaking the producer), enables independent scaling, and is the
natural backbone for cross-service consistency via sagas and read-model updates (see
`architecture-and-design.md`). The trade is eventual consistency and the operational
reality of at-least-once delivery, which the patterns below address.

## Kafka vs RabbitMQ

- **Apache Kafka** — a distributed, partitioned, replayable log. Best when you want
  durable event streams, high throughput, multiple independent consumers, and the
  ability to replay history. The default for event-driven microservices and event
  sourcing.
- **RabbitMQ** — a traditional message broker with flexible routing (exchanges,
  queues). Great for task queues, RPC-style messaging, and complex routing when you
  don't need a replayable log.

Choose Kafka for event streaming and broad fan-out; RabbitMQ for work queues and rich
routing. Both have first-class Spring support.

## Spring for Apache Kafka basics

Add `spring-kafka`. Produce with `KafkaTemplate`, consume with `@KafkaListener`:

```java
@Component
class OrderEventPublisher {
    private final KafkaTemplate<String, OrderPlaced> kafka;
    void publish(OrderPlaced e) { kafka.send("orders.placed", e.orderId(), e); }
}

@KafkaListener(topics = "orders.placed", groupId = "billing")
void on(OrderPlaced e) { /* handle idempotently */ }
```

Use the message **key** deliberately — Kafka guarantees ordering only within a
partition, and the key determines partition, so key by the entity id (e.g. orderId)
when per-entity ordering matters.

## Event design

- **Name events as past-tense facts** — `OrderPlaced`, `PaymentCaptured`. An event is
  something that happened, not a command telling another service what to do.
- **Include enough data** for consumers to act without immediately calling back to the
  producer ("event-carried state transfer"), but avoid dumping the entire aggregate if
  it bloats the event and leaks internals.
- **Version events from day one** (see schema evolution below).
- Carry correlation/trace ids so async flows remain traceable end-to-end (see
  `observability.md`).

## The dual-write problem & transactional outbox

The subtle trap: a handler updates its database *and* publishes an event. These are two
separate systems, so a crash between them leaves you inconsistent — DB updated but event
never sent, or event sent but DB rolled back. You cannot wrap a DB transaction and a
broker publish in one atomic transaction reliably.

The fix is the **transactional outbox**: within the same local DB transaction that
changes your data, insert the event into an `outbox` table. A separate relay process
(a poller, or change-data-capture like Debezium reading the DB log) reads the outbox and
publishes to the broker, marking rows sent. Now the event is guaranteed to be published
if and only if the data change committed. This is the standard, correct way to publish
events reliably — recommend it whenever a handler both writes and publishes.

## Idempotent consumers

Because delivery is **at-least-once**, every consumer must tolerate receiving the same
message more than once without double-applying its effect. Make handlers idempotent by:

- Deriving a natural idempotency key from the event (event id, or entity id + version)
  and recording processed keys, skipping duplicates.
- Designing operations to be naturally idempotent (upserts, set-to-value rather than
  increment).

An event-driven consumer that isn't idempotent is a correctness bug waiting for the
first redelivery — flag it in reviews.

## Error handling: retries & dead-letter

Decide what happens when a handler fails:

- **Transient failures** — retry with backoff (Spring Kafka's error handlers /
  `@RetryableTopic`).
- **Poison messages** (will never succeed) — route to a **dead-letter topic/queue**
  after N attempts so one bad message doesn't block the partition forever, and alert on
  DLQ depth. Silently dropping or infinitely retrying failed messages are both bugs.

## Schema evolution

Events outlive the code that first produced them, and producers and consumers deploy
independently, so schemas must evolve compatibly. Use a **schema registry** (e.g.
Confluent Schema Registry with Avro/Protobuf, or versioned JSON) and evolve
**backward/forward compatibly** — add optional fields, don't remove or repurpose
existing ones. Breaking a shared event schema is like breaking an API contract, but
worse because it can corrupt data asynchronously. Treat event schemas as first-class
contracts.
