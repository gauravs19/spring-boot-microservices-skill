# API Styles Beyond REST

REST over HTTP is the default and covers most needs (`rest-api-design.md`), but it isn't
always the right shape. This is a chooser for gRPC, GraphQL, and real-time push, with the
trade-offs that actually decide it.

## Table of contents
- [When REST isn't the right shape](#when-rest-isnt-the-right-shape)
- [gRPC](#grpc)
- [GraphQL](#graphql)
- [WebSocket & SSE (real-time push)](#websocket--sse-real-time-push)
- [Choosing](#choosing)

## When REST isn't the right shape

Reach past REST when: you need **high-throughput, low-latency internal** calls or
streaming (→ gRPC); **clients need to shape their own responses** and REST is causing
over-/under-fetching and endpoint sprawl (→ GraphQL); or you need **server-to-client
push** rather than request/response (→ WebSocket/SSE). If none of these bite, stay with
REST — it's simpler to operate, cache, and debug.

## gRPC

Contract-first RPC over HTTP/2 with Protobuf. Strengths: compact binary payloads, low
latency, first-class **streaming** (client/server/bidirectional), and a strongly-typed,
code-generated contract. Best for **internal service-to-service** calls where performance
matters and both ends are yours. Trade-offs: not natively browser-friendly (needs
grpc-web/a gateway), less human-inspectable than JSON, and more tooling. In Spring, use
the Spring gRPC support / a starter; keep the `.proto` files as the versioned contract and
evolve them compatibly (add fields, never renumber). Resilience/observability still apply
— wire timeouts, breakers, and tracing as with any remote call.

## GraphQL

A single endpoint where the **client specifies exactly the fields it wants**, solving
over-fetching and the "one screen needs five REST calls" problem — valuable for
aggregating data for varied front-ends (a BFF pattern). **Spring for GraphQL** provides
schema-first development with data-fetcher mappings. The trade-offs are real and must be
managed:

- **N+1 is the default failure mode** — resolving nested fields naively fires a query per
  parent. Use batching/**DataLoader** to collapse them (parallels the JPA N+1 problem in
  `persistence-and-data.md`).
- **Security & cost** — a flexible query language lets clients ask for expensive/deep
  queries; enforce **query depth/complexity limits**, and remember field-level
  authorization (a permissive resolver can leak data a REST endpoint wouldn't).
- **Caching** is harder than REST (no URL to cache on); plan for it.

Use GraphQL when diverse clients genuinely need flexible aggregation; don't adopt it just
to avoid designing REST resources — it trades endpoint design for resolver/complexity
management.

## WebSocket & SSE (real-time push)

When the server must push to the client:

- **Server-Sent Events (SSE)** — one-way server→client stream over plain HTTP. Simple,
  auto-reconnecting, proxy-friendly; ideal for notifications, live feeds, progress
  updates. Spring MVC/WebFlux support it directly. Prefer SSE when you only need
  server→client.
- **WebSocket** — full-duplex, bidirectional, persistent connection; for genuinely
  interactive/two-way real-time (chat, collaborative editing, live control). More to
  manage (connection state, scaling across replicas, often STOMP + a broker relay).

Default to **SSE** unless you need true bidirectional communication — it's much simpler to
operate. For documenting event-driven/streaming APIs, **AsyncAPI** is the OpenAPI
equivalent.

## Choosing

| Need | Style |
|---|---|
| Standard CRUD / resource API, broad compatibility | **REST** |
| Fast internal service-to-service, streaming, typed contract | **gRPC** |
| Diverse clients shaping their own responses / aggregation | **GraphQL** (mind N+1, depth limits) |
| Server→client updates only | **SSE** |
| Two-way real-time interaction | **WebSocket** |

Mixing is normal: REST/gRPC for core APIs, SSE/WebSocket for the real-time slice, GraphQL
at a BFF edge. Choose per need, not as a house religion.
