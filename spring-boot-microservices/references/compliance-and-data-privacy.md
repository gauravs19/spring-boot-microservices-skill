# Compliance & Data Privacy

Handling personal and sensitive data responsibly — the concerns that turn into legal and
trust liabilities when ignored, not just bugs. This is framework-neutral (GDPR/DSGVO,
CCPA, HIPAA, etc. share the same engineering primitives); it covers what a service must
do, and where Spring helps. It complements `security.md` (authn/authz/secrets).

## Table of contents
- [Know your data (classification & mapping)](#know-your-data-classification--mapping)
- [Data minimization](#data-minimization)
- [Encryption](#encryption)
- [Audit logging vs application logging](#audit-logging-vs-application-logging)
- [Never log PII or secrets](#never-log-pii-or-secrets)
- [Retention & the right to erasure](#retention--the-right-to-erasure)
- [Access control & tenant isolation](#access-control--tenant-isolation)

## Know your data (classification & mapping)

You can't protect what you haven't identified. Classify the data each service holds —
public, internal, **PII** (name, email, IDs), **sensitive** (health, financial,
biometric) — and know where it flows (which services, logs, caches, backups, analytics).
This data map is the foundation for every control below and for answering regulator/DPO
questions. In a microservices estate, the service that *owns* a piece of PII owns its
protection and its deletion.

## Data minimization

Collect and store the least you need, for as long as you need it. Every extra PII field
is extra liability in a breach. Don't propagate PII to services that don't need it — pass
an ID and let the owner resolve it, rather than copying names/emails into every event and
log. Minimization is the cheapest privacy control because data you don't hold can't leak.

## Encryption

- **In transit** — TLS everywhere, including internal hops in sensitive environments (a
  mesh can enforce mTLS; see `spring-cloud-infra.md`).
- **At rest** — database/disk/backup encryption (usually platform-provided).
- **Field-level / application-level** — encrypt especially sensitive fields
  (national IDs, tokens) in the application so they're ciphertext even to anyone with DB
  access, with keys held in a KMS/Vault (see `configuration-and-profiles.md`). Reserve
  this for the genuinely sensitive fields — it complicates search and indexing.

## Audit logging vs application logging

These are different and often conflated. **Application logs** are for debugging (verbose,
short retention, not authoritative). An **audit log** is a durable, tamper-evident record
of *who did what to what, when* — security-relevant and data-access events — kept for a
defined period to satisfy compliance and investigations. Design it deliberately:
append-only, protected from modification, and separate from noisy app logs. Spring helps
for entity-change history via **Hibernate Envers** (revision tables), and you can capture
access/security events with an interceptor/aspect or Spring Security events — but decide
*what* is auditable from the requirements, not by logging everything.

## Never log PII or secrets

The most common privacy leak is accidental: dumping a request/entity that contains PII,
or a token, into application logs — which then flow to an aggregator with broad access
and long retention. Mask or omit PII in logs, keep secrets out entirely (see
`security.md`), be careful with `toString()` on entities and with `spring.jpa.show-sql`
(which can echo values), and treat structured-log fields with the same care. Flag any
logging of personal data in review.

## Retention & the right to erasure

Data must not live forever. Define **retention periods** per data type and enforce them
(scheduled purge jobs — see `async-scheduling-and-batch.md`), and support **deletion on
request** ("right to be forgotten"). Deletion is genuinely hard in a distributed system:
the data may exist in the owning DB, downstream services' local copies, event logs,
caches, backups, and analytics. Practical approaches: propagate a "subject deleted" event
so each holder erases its copy, prefer references over copies so there's less to chase,
and use **crypto-shredding** (destroy the per-subject encryption key) for data you can't
easily hard-delete (immutable event logs, backups). Design deletion in from the start —
retrofitting it across an estate is painful.

## Access control & tenant isolation

Enforce least privilege on personal data — not every service or role needs it, and access
should be authorized and (for sensitive data) audited. In multi-tenant systems, guarantee
tenant isolation so one tenant's data is never returned to another: scope every query by
tenant, enforce it at a layer that can't be forgotten per-query (a filter/interceptor,
row-level security, or separate schemas), and test it explicitly — cross-tenant leakage
is both a severe bug and a compliance breach.
