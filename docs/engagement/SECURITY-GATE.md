# Security gate

This file describes the five security controls of this repo. The workflow is `.github/workflows/security.yml`.

## Jobs and fail rules

| Job | What it checks | Fails when |
|---|---|---|
| `sast` | Semgrep rules for Java (`p/java`) on `src`. CodeQL runs in `codeql.yml`. | Any error-level result. |
| `secrets` | Gitleaks over the full Git history with `.gitleaks.toml`. | Any finding. |
| `deps` | Trivy file scan of `pom.xml`. | HIGH or CRITICAL finding with a fix. |
| `image` | Build the image, then Trivy image scan. | HIGH or CRITICAL finding with a fix. |
| `sbom` | CycloneDX bill of materials from Maven, uploaded as an artifact. | The build fails. |
| `exceptions` | `scripts/check_security_exceptions.py` on `security/exceptions.json`. | An item lacks a field or has expired. |

A finding in `security/exceptions.json` that has not expired does not fail the job. The list is empty now.

## Run each check locally

```
gitleaks git --config .gitleaks.toml --redact --no-banner --exit-code 1 .
gitleaks dir --config .gitleaks.toml --redact --no-banner .
semgrep scan --config p/java --error --severity ERROR src
trivy fs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 --skip-dirs target,.data .
trivy image --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 localhost/payments-ledger-service:local
./mvnw -B -q org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom
actionlint .github/workflows/security.yml
python3 scripts/check_security_exceptions.py security/exceptions.json
scripts/test_check_security_exceptions.sh
```

## First scan result

The first Trivy scan found three HIGH findings in transitive libraries of the Kafka client: `lz4-java` 1.11.2, and `snappy-java` 1.1.10.7 (two findings). The fix is a pin in `pom.xml` to `lz4-java` 1.11.4 and `snappy-java` 1.1.10.9. After the pin, both scans report zero findings.

## Status

| Control | Status |
|---|---|
| Local runs of all checks above | Verified locally (see `evidence/security-gate-local.txt`, measured on one laptop) |
| Workflow file lint | Verified locally with actionlint and yamllint |
| Workflow run on GitHub | Authored, not run: no Actions run was started or checked for this work |
| Action versions | Unverified until a run |
| CodeQL | Authored, not run: it needs GitHub Actions |
