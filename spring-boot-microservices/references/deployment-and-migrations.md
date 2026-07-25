# Zero-Downtime Deployment & Database Migrations

The discipline that keeps rolling deploys from causing outages or data loss. The pieces
exist elsewhere (probes and graceful shutdown in `containerization-and-k8s.md`, Flyway
in `persistence-and-data.md`); this file is the *coherent method* for changing a running
system without downtime.

## Table of contents
- [The core rule: every change is backward-compatible](#the-core-rule-every-change-is-backward-compatible)
- [Rollout strategies](#rollout-strategies)
- [Expand/contract database migrations](#expandcontract-database-migrations)
- [Backward-compatible API changes](#backward-compatible-api-changes)
- [Decouple schema changes from code deploys](#decouple-schema-changes-from-code-deploys)
- [Feature flags & progressive delivery](#feature-flags--progressive-delivery)
- [Rollback](#rollback)

## The core rule: every change is backward-compatible

During a rolling deploy, **old and new versions of the service run simultaneously against
the same database**. So the only safe changes are ones where the old code still works
against the new schema and vice versa. Nearly every zero-downtime failure traces back to
violating this — a column renamed or dropped in the same release that stops using it,
and the still-running old pods start throwing. Internalize this and most of the rest
follows.

## Rollout strategies

- **Rolling update** (K8s default) — replace pods gradually. Requires backward-compatible
  changes and correct readiness/graceful-shutdown (below). The default for most services.
- **Blue-green** — stand up the new version alongside, switch traffic at once, keep the
  old for instant rollback. Good when you can't tolerate mixed versions, at the cost of
  double capacity.
- **Canary** — route a small % of traffic to the new version, watch metrics, ramp up.
  Best for catching regressions with real traffic before full exposure; pairs with
  progressive-delivery tooling (Argo Rollouts, Flagger).

For any of these, readiness must flip to *not-ready* on SIGTERM and the app must finish
in-flight requests during the grace period — otherwise "zero-downtime" drops requests on
every deploy.

## Expand/contract database migrations

The **expand/contract** (a.k.a. parallel-change) pattern is how you make breaking schema
changes safely, spread across multiple releases. Example — renaming `name` to
`full_name`:

1. **Expand** (release 1): add the new column `full_name` (nullable/defaulted). Schema is
   backward-compatible; old code ignores it.
2. **Migrate/dual-write** (release 1 code): new code writes **both** columns and reads
   the new one, falling back to the old. Backfill existing rows (`full_name = name`) in a
   batched migration.
3. **Contract** (release 2, after all old pods are gone and backfill is done): stop
   writing the old column; drop `name` in a later migration.

The rule inside each step: never drop or repurpose a column in the same release that
stops using it. The same pattern covers `NOT NULL` additions (add nullable → backfill →
add constraint), type changes (new column → migrate → swap), and table splits.

Also: keep migrations **forward-only** in production (Flyway), make them **idempotent and
non-locking** where possible (big `ALTER`s and backfills can lock tables — batch them),
and never let a single migration both change schema and depend on new code being live.

## Backward-compatible API changes

The same principle applies to your API contract, since consumers upgrade independently:
**add, don't change or remove**. New fields are optional; never remove or repurpose an
existing field or change its meaning in place; deprecate first, remove much later behind
a version. See `rest-api-design.md` for versioning. Event schemas follow the same rule
(`messaging-and-events.md`).

## Decouple schema changes from code deploys

Because migrations and code roll out as separate steps, treat them as separate: apply the
expand migration, deploy code that tolerates both shapes, then apply the contract
migration in a later cycle. Running `ddl-auto` or coupling a destructive migration to
app startup removes your ability to sequence this — which is why prod uses
`ddl-auto=validate` and managed migrations (see `persistence-and-data.md`).

## Feature flags & progressive delivery

Feature flags decouple **deploy** from **release**: ship code dark, turn it on
gradually (per-tenant, per-%), and kill it instantly without a redeploy if it misbehaves.
This is the safest way to release risky behavior and complements canary rollouts. Keep
flags short-lived (remove once fully rolled out — stale flags are their own tech debt),
and don't let flag logic sprawl into every method; centralize evaluation.

## Rollback

Design every deploy to be reversible: keep the previous image ready (blue-green makes
this instant; rolling relies on `kubectl rollout undo`). The subtle part is the
**database** — because you never made a destructive change in the same release, rolling
*code* back is safe. That's the real payoff of expand/contract: it keeps rollback a
non-event instead of a second incident.
