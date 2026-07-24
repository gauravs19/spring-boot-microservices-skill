# Bug-fix eval suite

Each task under `tasks/` is a small, self-contained Spring Boot app (JDK 21, Maven,
Spring Boot 3.3.5) with **one real bug and a failing JUnit test**. The task is to make
`mvn test` pass by fixing the application source. Ground truth is the test result, not
anyone's judgment.

These are deliberately **domain-relevant** (real Spring Boot mistakes) rather than
drawn from a generic Java bug corpus — a Spring Boot skill can only show its effect on
Spring Boot code.

## The tasks

| Task | Planted bug | Correct fix (kept in the failing test's intent) |
|---|---|---|
| `task1-validation` | `@RequestBody` DTO missing `@Valid`, so constraints never fire — invalid input returns 201 | Add `@Valid` → `MethodArgumentNotValidException` → 400 |
| `task2-errormodel` | `Optional.get()` on a missing entity → 500 | Return 404 (`orElseThrow(ResponseStatusException NOT_FOUND)` or an `@ExceptionHandler`) |
| `task3-security` | `SecurityFilterChain` secures everything, so the public path returns 401 | Add `.requestMatchers("/public/**").permitAll()` before `anyRequest().authenticated()` |

## How to run

The experiment is: give each task to the agent **with** the skill and **without** it
(baseline), in separate copies, then grade objectively.

1. Copy a task to a work dir per arm (so the arms don't collide):
   ```bash
   cp -r tasks/task1-validation /tmp/run/task1/with_skill
   cp -r tasks/task1-validation /tmp/run/task1/baseline
   ```
2. Run the agent on each copy. Tell it only: *"there is a failing test; make `mvn test`
   pass; do not modify anything under `src/test`."* For the with-skill arm, also point
   it at the skill's `SKILL.md`.
3. Grade objectively:
   ```bash
   ./grade.sh /tmp/run/task1/with_skill
   ./grade.sh /tmp/run/task1/baseline
   ```
   `grade.sh` runs `mvn -q test`, reports PASS/FAIL from the real exit code, and
   verifies `src/test` was not modified (a run that edited its own tests is
   disqualified).

## Expected outcome

Roughly equal pass rates between arms — see `../RESULTS.md`. This suite exists to catch
**regressions** (the skill making things worse or misleading the model on concrete
code), not to demonstrate lift. Lift lives in `../review/`.
