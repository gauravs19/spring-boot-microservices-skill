#!/usr/bin/env bash
# Objective grader for a bug-fix task attempt.
#
# Usage: ./grade.sh <path-to-attempted-task-dir> [path-to-original-task-dir]
#
# - Runs `mvn -q test` in the attempt dir; PASS iff the build succeeds (exit 0).
# - If the original task dir is given, verifies src/test is unchanged, so an
#   attempt that "passed" by editing the tests is flagged DISQUALIFIED.
set -u

attempt="${1:?usage: grade.sh <attempt-dir> [original-dir]}"
original="${2:-}"

if [[ ! -f "$attempt/pom.xml" ]]; then
  echo "ERROR: no pom.xml in $attempt"; exit 2
fi

( cd "$attempt" && mvn -q test >/dev/null 2>&1 ); rc=$?

tests_ok="n/a"
if [[ -n "$original" && -d "$original/src/test" ]]; then
  if diff -rq "$original/src/test" "$attempt/src/test" >/dev/null 2>&1; then
    tests_ok="unchanged"
  else
    tests_ok="MODIFIED"
  fi
fi

if [[ $rc -eq 0 && "$tests_ok" == "MODIFIED" ]]; then
  verdict="DISQUALIFIED (tests were modified)"
elif [[ $rc -eq 0 ]]; then
  verdict="PASS"
else
  verdict="FAIL"
fi

printf "%-12s mvn_exit=%s tests=%s -> %s\n" "$(basename "$attempt")" "$rc" "$tests_ok" "$verdict"
[[ "$verdict" == "PASS" ]]
