# Review eval suite

`flawed-service/` is a deliberately flawed Spring Boot service (a legacy Boot 2.7 /
Java 8 "orders" service) with a set of planted, labelled defects spanning build,
config, entity, controller, and security. `answer-key.md` lists them.

This suite measures the dimension where the skill actually adds value: **review
quality** — how many real issues are caught, how well they're prioritised, whether the
guidance reflects modern standards, and whether functional bugs (not just convention
violations) are found.

## Note on the fixture's "secrets"

`application.properties` contains realistic-looking but **entirely fake** credentials
(`Sup3rS3cret!Prod`, `ak_live_...`). They are intentional planted findings for the
security dimension — not real secrets. Don't "fix" them in the fixture.

## How to run

1. Have a coding agent review `flawed-service/` **with** the skill (point it at
   `SKILL.md`, Mode 3) and, separately, **without** it (baseline).
2. Score each review against `answer-key.md`:
   - **Recall** = planted issues correctly reported / total planted.
   - **Precision** = correct reports / all reports (penalise fabricated issues).
   - **Prioritisation** = are the Critical items actually ranked at the top?
   - **Logic-bug catch** = did it find the functional bug (ignored inventory result /
     wrong key), not just the convention issues? (This is the one the standards-only
     pass tends to miss — the reason the v1.1 "correctness-first" step exists.)
3. Optionally run a **blind judge**: give both reviews (unlabelled) to an independent
   agent and ask which is the better review and why.

## Why not just a pass/fail number

Review quality is partly subjective (was the prioritisation sensible? was the advice
current?), so this suite pairs the objective recall/precision against the answer key
with a qualitative blind-judge pass. That combination is more honest than forcing a
single score onto something that genuinely needs judgment.
