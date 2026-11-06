# Parquet Jackson verification — 2026-11-06

This records the D04 replacement separately from the [historical assessment](SECURITY_REVIEW_2026-11-05.md). Historical findings, counts, recommendations, Appendix A and Fixed checkboxes are unchanged. See the [PR summary](PARQUET_JACKSON_PR_SUMMARY.md) for the change and candidate identity.

## Build and dependency checks

The source and Glue/MSK candidate graphs each contain the same 19 inspected consumer configurations. Both have zero unresolved dependencies and exactly one replacement in each of:

| Project | Configuration |
| --- | --- |
| `iceberg-kafka-connect` | `runtimeClasspath` |
| `iceberg-kafka-connect` | `testRuntimeClasspath` |
| `iceberg-kafka-connect-runtime` | `runtimeClasspath` |
| `iceberg-kafka-connect-runtime` | `testRuntimeClasspath` |
| `iceberg-kafka-connect-runtime` | `hive` |

The other 14 configurations have no replacement. Resolving test configurations did not execute tests. Comparing each graph with the preceding compression verification found only the Parquet Jackson substitution and generated local project versions changing. Parquet 1.15.2, Iceberg 1.5.2 and the earlier dependency fixes remain aligned.

`jacksonRuntime` resolves exactly core, databind and annotations 2.18.11 under the enforced BOM. It is not consumable. `apiElements` and `runtimeElements` each expose only the patched JAR, have no unshaded Jackson implementation dependencies and have no secondary class-directory variants. The extra Shadow runtime configuration is not consumable.

Compilation and the recovered Glue/MSK `distZip` recipe passed. The candidate contains 175 JARs, including exactly one patched bundle and no original `parquet-jackson` JAR. Every JAR was inventoried for classes, Maven metadata and nested archives. There are no nested archives. The 171 other dependency JARs are byte-identical to the previous candidate; only manifests change inside the three rebuilt connector JARs. This preserves the earlier Parquet, Netty, standalone Jackson, Hadoop/Guava and compression fixes.

## Bundle inspection and reproducibility

| Check | Result |
| --- | --- |
| Core base classes | 211 / 211 preserved and relocated |
| Databind base classes | 789 / 789 preserved and relocated |
| Annotations base classes | 73 / 73 preserved and relocated |
| Total | 1,073 / 1,073; no minimization |
| Bytecode | Maximum class major version 52 (Java 8) |
| Maven identification | All three POMs and `pom.properties` preserved byte-for-byte at 2.18.11 |
| Package-version bytecode | Core and databind `PackageVersion` constants both 2.18.11 |
| Attribution | All input LICENSE/NOTICE contents retained, including both FastDoubleParser resources and Schubfach |
| Services | Relocated `JsonFactory` and `ObjectCodec` filenames and providers; each provider class exists |
| Exclusions | No unrelocated implementation classes, signatures, module descriptors or `META-INF/versions/**` |
| Omitted overrides | All seven versioned implementation entries have base counterparts |
| Identity | Local title/vendor/version only; no upstream Parquet Maven identity or variable build fields |
| Reproducibility | Forced rebuild has identical SHA-256: `4acc5afa2086d1d00468a70f99de54c2f44dda241c8e5eb033d4a53f710ed78a` |

The original Parquet bundle is used only as comparison evidence, never as a build input. The replacement has no `Multi-Release` manifest attribute, matching the original bundle's loading behavior and avoiding Shadow 8.1.1 processing Java 21 overrides.

Static inspection indexed **62,884 classes** in each extracted distribution and the union of **1,109 old/new relocated class names**. It checked class, method and field references entering and leaving the replacement library, including 24 method references and one field reference into relocated Jackson from other JARs. JDK classes and JVM array members were resolved statically. Both distributions have zero unresolved references in this scope, and the candidate introduces zero errors. This does not execute application classes or establish runtime behavior, reflective compatibility or integration correctness.

## Scanner comparison

Both distributions were rescanned from their exact extracted JAR bytes with **Trivy 0.76.1 `rootfs --scanners vuln`**, offline, with no database updates. Scanner and full database bytes match the preceding compression verification:

| Input | Updated at (UTC) | SHA-256 |
| --- | --- | --- |
| Scanner binary | Version 0.76.1 | `5c03401caf9d3cc04d9e0c7f3abd102e97518e8237465cce702162b605535e8d` |
| Vulnerability DB | 2026-11-05 01:19:45.907162652 | `68917cddd951d3d29ab1e6bc4e1a33cf3bf160743d19276290b77afad13cfa98` |
| Java DB | 2026-11-05 01:08:50.735678238 | `823874792b6e8300a0f7a19c4031771b818654efbcefdb700f90115ed1a040e2` |

The previous rescan reproduces all 18 recorded findings exactly. The candidate reports 185 package records, explicitly identifying embedded core, databind and annotations at 2.18.11. It has **one finding**, Wire/D09 (`com.squareup.wire:wire-runtime-jvm:5.2.0`, `CVE-2026-45799`), **zero D04 findings**, and **zero new findings**. AWS's separately embedded Jackson is unchanged and outside this remediation.

Historical D04 refers to the original 14 matches in embedded core 2.13.4 / databind 2.13.4.2. Current D04 refers to 17 matches in embedded core/databind 2.18.1 in the preceding Parquet 1.15.2 bundle. They are separate baselines; their reconciliation is recorded below. A dash means the advisory was absent from that baseline.

| Component | Advisory | Historical | Current | Candidate |
| --- | --- | --- | --- | --- |
| core | CVE-2025-52999 | Present | — | Absent |
| core | CVE-2026-18401 | — | Present | Absent |
| core | CVE-2026-68494 | Present | Present | Absent |
| core | CVE-2026-89407 | — | Present | Absent |
| core | CVE-2026-89425 | Present | Present | Absent |
| databind | CVE-2026-19032 | Present | Present | Absent |
| databind | CVE-2026-50193 | Present | — | Absent |
| databind | CVE-2026-54512 | Present | Present | Absent |
| databind | CVE-2026-54513 | Present | Present | Absent |
| databind | CVE-2026-54514 | Present | Present | Absent |
| databind | CVE-2026-54515 | Present | Present | Absent |
| databind | CVE-2026-59888 | — | Present | Absent |
| databind | CVE-2026-59889 | Present | Present | Absent |
| databind | CVE-2026-68497 | — | Present | Absent |
| databind | CVE-2026-77310 | Present | Present | Absent |
| databind | CVE-2026-83557 | Present | Present | Absent |
| databind | CVE-2026-91776 | Present | Present | Absent |
| databind | CVE-2026-91777 | Present | Present | Absent |
| databind | GHSA-mhm7-754m-9p8w | — | Present | Absent |
| **Matches** | | **14** | **17** | **0** |

## Local reproduction and evidence

All new outputs are isolated under ignored `build/parquet-jackson-verification/`. Its Gradle cache, Glue Maven inputs, scanner and recorded databases were copied from `build/compression-verification/`; the existing verification JDK was reused. No historical output was overwritten: all **1,228** recorded historical evidence hashes still match.

From the repository root, using those preserved local inputs:

```sh
export JAVA_HOME="$PWD/build/netty-verification/jdk17/Contents/Home"
export GRADLE_USER_HOME="$PWD/build/parquet-jackson-verification/gradle"

./gradlew --offline --no-daemon --console=plain \
  --project-cache-dir build/parquet-jackson-verification/project-cache \
  -I build/parquet-jackson-verification/verification.init.gradle \
  -PverificationLabel=source inspectParquetJacksonResolution

./gradlew --offline --no-daemon --console=plain \
  --project-cache-dir build/parquet-jackson-verification/project-cache \
  -I build/parquet-jackson-verification/candidate.init.gradle \
  -I build/parquet-jackson-verification/verification.init.gradle \
  inspectParquetJacksonResolution \
  :iceberg-kafka-connect:compileJava \
  :iceberg-kafka-connect-events:compileJava \
  :iceberg-kafka-connect-transforms:compileJava \
  :iceberg-kafka-connect-runtime:distZip

./gradlew --offline --no-daemon --console=plain \
  --project-cache-dir build/parquet-jackson-verification/project-cache \
  -I build/parquet-jackson-verification/verification.init.gradle \
  :parquet-jackson-patched:shadowJar --rerun-tasks

build/parquet-jackson-verification/tools/trivy rootfs \
  --cache-dir build/parquet-jackson-verification/trivy-cache \
  --scanners vuln --pkg-types library --offline-scan \
  --skip-db-update --skip-java-db-update --disable-telemetry --skip-version-check \
  --list-all-pkgs --format json \
  --output build/parquet-jackson-verification/candidate-trivy.json \
  build/parquet-jackson-verification/scan-input/candidate
```

The previous distribution was scanned with the same flags against `scan-input/previous`. Archive inspection is in `verify_candidate.py`; advisory and acceptance reconciliation is in `reconcile.py`. Static inspection uses `LinkageInspection.java` with ASM 9.4 and the verification JDK, without loading application classes.

Evidence in that directory includes `source-resolution.json`, `candidate-resolution.json`, the corresponding `*-dependency-changes.json`, `candidate-artifact.json`, `candidate-jar-inventory.json`, `package-version-bytecode.txt`, `bundle-reproducibility.json`, `linkage-inspection.log`, the three empty `*-linkage-errors.txt` files, both `*-trivy.json` reports, `acceptance-checks.json`, and `historical-sha256.json`.

These local inputs and outputs are not committed. Preserve them separately to reproduce this exact comparison. Rebuilding connector manifests can change the distribution checksum; only the patched bundle is asserted reproducible. No application/smoke tests, GitHub checks, deployment or PR creation were performed; runtime compatibility remains unverified.
