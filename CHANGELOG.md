# Changelog

## 1.2.0

Targets the token overhead that v1.1's triage gate failed to reduce (see
`evals/RESULTS.md`).

### Changed
- **Slimmed the always-loaded `SKILL.md` body by ~48%** (341 → 179 lines) — the
  structural source of the per-invocation token overhead. Verbose cross-cutting
  principles / anti-patterns and the review report-format template moved into
  references; the body is now a lean router plus the irreducible judgment.
- **Measured effect:** bug-fix suite token overhead dropped from ~+21% to ~+13% over
  baseline (~48.5k → ~45.5k tokens/task), correctness unchanged (3/3, no test
  tampering). Roughly a third of the excess overhead removed. The residual is the
  irreducible cost of loading the router; driving it lower is a triggering-tuning
  problem, not a content one.
- Enhanced README (detailed mode/coverage/reference tables, usage examples, measured
  validation).

### Added
- `references/principles-and-anti-patterns.md` — the cross-cutting principles and
  anti-patterns (with reasoning) that previously lived inline in `SKILL.md`.
- Review report-format template moved into `references/review-checklist.md`.

## 1.1.0

Driven by the repo's own evals (`evals/`, results in `evals/RESULTS.md`).

### Added
- **`references/decisions-and-playbooks.md`** — decision tables for the ambiguous forks
  (monolith vs microservices, sync vs async, JPA/JDBC/R2DBC, MVC+virtual-threads vs
  WebFlux, split-vs-keep, config-server vs K8s) and diagnostic playbooks for
  underspecified symptoms ("slow endpoint", "intermittent 500s", "flaky in prod",
  "is this production-ready"). This is where the skill adds most over a bare model.
- **`evals/`** — reproducible evaluations: a `mvn test`-graded bug-fix suite
  (regression guard) and a labelled flawed-service review suite with answer key
  (where the skill's lift shows), plus a results log and grader script.

### Changed
- **Review mode now runs a correctness-first pass** before the standards checklist —
  trace what the code does vs. intends (ignored results, wrong identifiers, logic that
  contradicts its comment) so functional bugs aren't missed while auditing conventions.
  Added as a validated fix for a real miss found in testing.
- **Added an "effort-matching" triage gate** to `SKILL.md`: apply idioms directly on
  narrow, well-specified tasks and skip deep reference reading; reserve the references
  and playbooks for open-ended/ambiguous work. (Note: a rerun showed this did **not**
  measurably reduce token overhead on trivial tasks — that cost is structural, from the
  always-loaded `SKILL.md` body. See `evals/RESULTS.md`. The gate still correctly steers
  effort; token reduction is deferred to a leaner body / triggering tuning.)
- **Rebalanced depth** — build/scaffold mode kept lean (a strong model is already good
  there); design and review modes carry the richer guidance.

## 1.0.0

- Initial release: design / scaffold / review skill for modern Spring Boot
  microservices (Spring Boot 4.x / Spring Framework 7 / Java 25 LTS, Maven + Gradle),
  full modern stack, installable as a Claude Code plugin marketplace.
