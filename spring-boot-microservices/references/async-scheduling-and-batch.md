# Async, Scheduling & Batch

Work that doesn't happen inside a request/response: background tasks, scheduled jobs, and
bulk processing. The recurring trap in a microservice (multiple replicas) is that naive
scheduling and async code behave differently than on a single box — this file is about
getting that right.

## Table of contents
- [@Async background work](#async-background-work)
- [@Scheduled and the multi-replica trap](#scheduled-and-the-multi-replica-trap)
- [Distributed locking (ShedLock) & leader election](#distributed-locking-shedlock--leader-election)
- [Long-running jobs](#long-running-jobs)
- [Spring Batch for bulk processing](#spring-batch-for-bulk-processing)
- [Job reliability & observability](#job-reliability--observability)
- [In-app scheduling vs Kubernetes CronJob](#in-app-scheduling-vs-kubernetes-cronjob)

## @Async background work

`@Async` (with `@EnableAsync`) runs a method on another thread so the caller doesn't
block — useful for fire-and-forget side effects (sending a notification) where the caller
doesn't need the result. Configure an explicit executor rather than relying on the
default; on Java 21+ a virtual-thread executor is a great fit for I/O-bound async work.
Two cautions: exceptions in an `@Async void` method vanish unless you handle them (return
`CompletableFuture` or set an `AsyncUncaughtExceptionHandler`), and async work started in
a request loses the request's transaction/security context unless you propagate it.

## @Scheduled and the multi-replica trap

`@Scheduled` runs a method on a timer — but in a service scaled to N replicas, **every
replica runs it**, so a "nightly report" fires N times, a cleanup runs concurrently N
times, and you get duplicates or races. This is the single most common scheduling bug in
microservices. A `@Scheduled` job in a horizontally-scaled service is a red flag unless
it's explicitly made single-execution (below) or is genuinely safe to run on every pod.

## Distributed locking (ShedLock) & leader election

To make a scheduled task run **once across the fleet**:

- **ShedLock** — the simplest fix. It wraps the scheduled method in a lock backed by a
  shared store (the DB, Redis) so only one replica executes each firing; the others skip.
  Add `@SchedulerLock(name = "...")` with a max lock time. Ideal for periodic jobs.
- **Leader election** — one replica is elected leader and owns scheduled work (via
  Kubernetes lease, or Spring Cloud/ZooKeeper). More machinery; use when a single node
  must own an ongoing responsibility, not just a periodic tick.

Either way, still make the job **idempotent** — locks can expire mid-run, and you want a
re-run to be safe.

## Long-running jobs

Never do long/expensive work on the request thread — the client times out and you tie up
capacity. Accept the request, return **202 Accepted** with a status/location, and process
off-thread: enqueue to a broker and let a worker consume (durable, scalable, survives
restarts — see `messaging-and-events.md`), or hand to a background executor for smaller
in-process work. Expose a way to poll status. This keeps the API responsive and the work
observable and retryable.

## Spring Batch for bulk processing

For large chunked ETL/import/report jobs, **Spring Batch** provides the right structure:
read-process-write in chunks with transaction boundaries per chunk, restartability from
the last successful point, skip/retry policies, and job metadata. Reach for it when a job
processes enough records that "load it all and loop" would blow memory or can't afford to
restart from zero on failure. For simple periodic tasks it's overkill — a `@Scheduled` +
ShedLock method is enough.

## Job reliability & observability

Background work fails silently far too easily, so treat jobs as first-class:

- **Idempotent & retryable** — assume a job may run twice or resume after a crash.
- **Metrics & alerts** — emit success/failure/duration/last-run metrics (Micrometer) and
  alert on "job hasn't succeeded in N hours". A cron that silently stopped is a classic
  incident. See `observability.md`.
- **Graceful shutdown** — on SIGTERM, let an in-flight job finish or checkpoint; don't
  leave half-done work. Align with the pod termination grace period.
- **Timeouts & dead-letter** for queued work (`messaging-and-events.md`).

## In-app scheduling vs Kubernetes CronJob

- **In-app `@Scheduled` (+ ShedLock)** — the job shares the service's code, config, and
  deployment; good when it needs the app's domain logic and beans.
- **Kubernetes `CronJob`** — a separate short-lived pod runs the task on a schedule;
  naturally single-execution (no multi-replica trap), isolated resource usage, good for
  ops/maintenance tasks or jobs you don't want competing with request traffic. The trade
  is a separate artifact/entrypoint to maintain.

Choose in-app when the job is domain logic tightly coupled to the service; choose CronJob
when it's isolated, heavy, or you want it off the serving pods.
