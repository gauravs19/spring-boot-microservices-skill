# Eval results

Runs performed on Opus-class models via subagents. Numbers are from small-N runs —
treat them as directional, not leaderboard-precise. Methodology and fixtures are in
`bugfix/` and `review/`.

## Bug-fix suite (regression guard)

N=3 tasks, skill-on vs no-skill baseline, graded objectively by `mvn test`.

| Run | Resolved | Tests modified | Avg tokens / task | Avg wall-time / task |
|---|---|---|---|---|
| Baseline (no skill) | 3/3 | none | ~40,200 | ~206 s |
| v1.0 skill-on | 3/3 | none | ~47,500 | ~198 s |
| v1.1 skill-on (rerun) | 3/3 | none | ~48,500 | ~139 s |

**Read:** no pass-rate lift in any run — the *expected and desired* result for this
suite. A capable model already fixes well-specified bugs; the suite's job is to prove
the skill doesn't **regress** correctness or mislead on concrete code. It doesn't
(3/3, no test tampering, across both versions). Lift is not expected here — see the
review suite.

**Honest note on the token overhead (v1.1 rerun):** v1.1's "match effort to the task"
triage gate was intended to cut the ~+18% token overhead on trivial tasks. The rerun
shows it **did not** — v1.1 skill-on is ~48.5k tokens vs v1.0's ~47.5k (flat, slightly
up), still ~+21% over baseline. The overhead is **structural**: it's the cost of the
always-loaded `SKILL.md` body, which the gate can't remove (and v1.1's body is slightly
larger). The gate only avoids *reference-file* reads, and on these already-narrow tasks
the agents weren't deep-reading references much anyway, so there was little to save. The
wall-time drop is real but unreliable across separate runs (machine-load sensitive) and
is not claimed as a win. Cutting trivial-task overhead needs a **leaner SKILL.md body**
or **triggering tuning** so the skill doesn't fire on one-liners at all — deferred to a
future version.

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
