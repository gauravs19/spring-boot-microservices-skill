# Eval results

Runs performed on Opus-class models via subagents. Numbers are from small-N runs —
treat them as directional, not leaderboard-precise. Methodology and fixtures are in
`bugfix/` and `review/`.

## Bug-fix suite (regression guard)

N=3 tasks, skill-on vs no-skill baseline, graded objectively by `mvn test`.

| Metric | With skill | Baseline |
|---|---|---|
| Resolved | **3/3 (100%)** | **3/3 (100%)** |
| Tests modified (cheating) | none | none |
| Avg tokens / task | ~47,500 | ~40,200 |
| Avg wall-time / task | ~198 s | ~206 s |

**Read:** no pass-rate lift, at ~18% more tokens. This is the *expected and desired*
result for this suite — a capable model already fixes well-specified bugs, and the
suite's job is to prove the skill doesn't **regress** correctness or mislead on
concrete code. It doesn't. Lift is not expected here; see the review suite.

The ~18% token overhead on trivial tasks is exactly what v1.1's "match effort to the
task" triage gate targets: for a single well-specified change the skill now tells the
model to apply the idiom directly and skip deep reference reading.

## Review suite (where the lift is)

Reviewing the labelled `flawed-service` (17 planted defects) against `answer-key.md`.

| | v1.0 skill | Baseline (no skill) | v1.1 skill |
|---|---|---|---|
| Standards issues caught | 16/17 | ~15/17 | 17/17 |
| Prioritisation (Criticals on top) | good | good | good |
| **Logic bug (#5: ignored stock result)** | **missed** | caught | **caught — ranked #1 Critical** |
| Modern-standards framing (Problem Details, Resilience4j, BOM, virtual threads) | strong | generic | strong |

**Read:** v1.0 was strong on conventions but — following its checklist too faithfully
— missed the functional bug that a good engineer catches first. The baseline caught it
precisely because it wasn't running a checklist. v1.1 adds a **correctness-first pass**
(trace what the code does vs. intends, *before* the standards checklist); with it the
skill now catches the logic bug and ranks it #1, and additionally surfaced two things
neither earlier pass found (the Actuator wildcard is inert until the starter is added;
the API key is configured but never sent). v1.1 now leads on both the correctness and
standards axes.

## What the results drove (v1.1 changes)

- **Correctness-first review step** — from the v1.0 logic-bug miss. *(validated above)*
- **"Match effort to the task" triage gate** — from the bug-fix token overhead.
- **`decisions-and-playbooks.md`** — decision tables + diagnostic playbooks, targeting
  the ambiguous/underspecified work the bug-fix suite can't exercise.
- **Leaner build mode, richer design/review** — depth reallocated to where lift shows.
- **This `evals/` harness** — so future changes are re-measured, not vibe-checked.
