# CI/CD & Software Supply Chain

How a service actually reaches production safely. Building an image is the easy part
(see `containerization-and-k8s.md`); this file is the pipeline around it — the gates,
scanning, provenance, and promotion that stop bad or compromised code from shipping.

## Table of contents
- [Pipeline stages & gates](#pipeline-stages--gates)
- [Reproducible builds](#reproducible-builds)
- [Dependency & vulnerability scanning](#dependency--vulnerability-scanning)
- [SBOM (software bill of materials)](#sbom-software-bill-of-materials)
- [Image signing & provenance](#image-signing--provenance)
- [Secrets in CI](#secrets-in-ci)
- [Artifact promotion & environments](#artifact-promotion--environments)

## Pipeline stages & gates

A microservice pipeline should run, in order, with each stage a **gate that fails the
build** (not a warning nobody reads):

1. **Build & unit/slice tests** — fast feedback.
2. **Integration tests** — Testcontainers-backed (see `testing.md`).
3. **Static analysis & quality** — compiler warnings, SpotBugs/PMD/Checkstyle or
   Sonar; fail on new critical issues.
4. **Dependency & vulnerability scan** — fail on known high/critical CVEs (below).
5. **Build the image** — buildpacks or layered Dockerfile.
6. **Scan the image** — Trivy/Grype on the built image.
7. **Generate SBOM + sign the image** (below).
8. **Publish** to the registry; **promote** through environments.

The discipline that matters: gates are enforced, and the pipeline is the *only* path to
production — no manual "push from my laptop", which bypasses every check above.

## Reproducible builds

Pin everything so the same commit produces the same artifact: pinned dependency
versions (the Spring BOM handles most; lock plugin versions too), a pinned base image
digest (not a floating `latest`), and a pinned build tool (the Maven/Gradle wrapper
committed to the repo). Reproducibility is what makes an SBOM and a signature
meaningful — otherwise you can't say *what* you actually shipped.

## Dependency & vulnerability scanning

A known-CVE dependency is one of the highest-probability real incidents, so scan on
every build and on a schedule (new CVEs land against old code):

- **Dependabot / Renovate** — automated dependency-update PRs.
- **OWASP Dependency-Check**, **Snyk**, or **Trivy** (fs mode) — scan declared
  dependencies; fail the build on high/critical.
- **Trivy/Grype** — scan the built *image* too (base-image OS packages, not just Java
  deps).

Fail the pipeline on high/critical with a documented, time-boxed exception process —
don't let "we'll fix it later" become permanent.

## SBOM (software bill of materials)

Generate a **CycloneDX** (or SPDX) SBOM as a build artifact — a machine-readable list of
every component and version in the release. The CycloneDX Maven/Gradle plugins produce
it directly. The SBOM is what lets you answer "are we affected by CVE-X?" across dozens
of services in minutes instead of days — invaluable during the next Log4Shell-class
event. Store it alongside the image.

## Image signing & provenance

Sign published images with **cosign** (Sigstore) so deployers can verify an image came
from your pipeline and wasn't tampered with. Go further with **SLSA provenance**
attestations describing how/where the artifact was built. Then enforce at the cluster
with an admission policy (e.g. Kyverno/Cosign policy) that **only signed images from your
pipeline run** — closing the "someone pushed a malicious image" path. This is the core
of supply-chain integrity and increasingly a compliance expectation.

## Secrets in CI

CI is a high-value target — a leaked CI secret is a production breach. Use the CI
platform's secret store or an external manager (Vault/cloud), never plaintext in the
workflow file or logs; prefer **short-lived OIDC-federated credentials** to the cloud
over long-lived static keys; scope tokens minimally; and scan the repo for accidentally
committed secrets (gitleaks/trufflehog) as a pipeline gate.

## Artifact promotion & environments

**Build once, promote the same artifact** through dev → staging → prod, changing only
injected config (see `configuration-and-profiles.md`). Rebuilding per environment breaks
the chain — the thing you tested is no longer the thing you ship, and the signature/SBOM
no longer describe production. Gate promotion to prod on the appropriate approvals and
on staging health.
