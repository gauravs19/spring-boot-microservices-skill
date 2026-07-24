# Eval results

Runs performed on Opus-class models via subagents. Numbers are from small-N runs —
treat them as directional, not leaderboard-precise. Methodology and fixtures are in
`bugfix/` and `review/`.

## Bug-fix suite (regression guard)

N=3 tasks, skill-on vs no-skill baseline, graded objectively by `mvn test`.

| Run | Resolved | Tests modified | Avg tokens / task | Overhead vs baseline |
|---|---|---|---|---|
| Baseline (no skill) | 3/3 | none | ~40,200 | — |
| v1.0 skill-on | 3/3 | none | ~47,500 | +7,300 (+18%) |
| v1.1 skill-on | 3/3 | none | ~48,500 | +8,300 (+21%) |
| v1.2 skill-on (slimmed body) | 3/3 | none | **~45,500** | **+5,300 (+13%)** |

**Read:** no pass-rate lift in any run — the *expected and desired* result for this
suite. A capable model already fixes well-specified bugs; the suite's job is to prove
the skill doesn't **regress** correctness or mislead on concrete code. It doesn't
(3/3, no test tampering, every version). Lift is not expected here — see the review suite.

**Token overhead — the honest arc:**

- **v1.1** added a "match effort to the task" triage gate meant to cut the overhead. A
  rerun showed it **did not** (~48.5k, flat/slightly up). The overhead is *structural* —
  the always-loaded `SKILL.md` body — which a gate *inside* that body can't remove. The
  gate only avoids *reference* reads, and on already-narrow tasks there was little of
  that to save.
- **v1.2** attacked the actual cause: the `SKILL.md` body was cut **48%** (341→179
  lines) by moving verbose principles/anti-patterns and the review-format template into
  on-demand references. That reduced overhead from +21% to **+13%** — roughly **a third
  of the excess removed**, correctness unchanged (3/3).

The residual ~+13% is the irreducible cost of loading the router body at all. Driving it
lower would require **triggering tuning** so the skill doesn't fire on one-liners (the
description-optimization loop — blocked on this Windows box, needs a Linux/WSL run). Net:
the body-slim fix worked and is banked; the last mile is a triggering problem, not a
content problem.

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
