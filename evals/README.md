# Evals

Reproducible evaluations for the `spring-boot-microservices` skill. The point of
shipping these is honesty: a skill should be able to show what it does and doesn't
improve, measured, not asserted.

There are two suites, because the skill's value shows up in two very different places:

| Suite | What it measures | Where the skill helps |
|---|---|---|
| [`bugfix/`](bugfix/) | Objective, test-graded bug-fix ability (skill-on vs baseline) | **Regression guard.** Confirms the skill is correct and non-harmful; expect little/no *lift* here — a strong model already fixes well-specified bugs. |
| [`review/`](review/) | Review quality against a labelled flawed service (precision/recall + blind judge) | **Where the lift is.** Open-ended judgment: catching issues, prioritising, and modern-standards guidance. |

See [`RESULTS.md`](RESULTS.md) for the numbers from the runs done so far, and the
methodology note there for why the bug-fix suite is expected to show no lift (and why
that's a useful result, not a disappointing one).

## Why two suites

A test-graded fix benchmark (à la SWE-bench) rewards raw fix ability — exactly the
dimension where a capable base model needs the least help. It's the right tool to
prove the skill doesn't *regress* anything, but the wrong tool to show its value. The
review suite targets the dimension the skill actually moves: judgment on
underspecified, multi-concern work.

## Requirements

- JDK 21+ and Maven (bug-fix suite builds Spring Boot 3.3.5 apps).
- For the review suite: any capable coding agent to run the review, plus the answer
  key for scoring.
