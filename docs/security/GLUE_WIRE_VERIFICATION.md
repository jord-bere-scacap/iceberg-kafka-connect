# Glue/Wire verification — 2026-11-06

**Acceptance: blocked by static linkage.** The candidate clears D09 and the Scala
finding, with zero reported dependency findings. The requirement for zero newly
unresolved static references remains unmet; the Scala patch adds no linkage errors.
The [historical assessment](SECURITY_REVIEW_2026-11-05.md) and its checkboxes remain
unchanged. The [copyable PR summary](GLUE_WIRE_PR_SUMMARY.md) describes this candidate.

## Build and packaging

- Glue modules: **2.0.1**, aligned by shared dependency rules.
- Wire modules: **7.0.1**, aligned by shared dependency rules.
- AWS SDK v2: **2.32.25**, enforced BOM in the opt-in configuration.
- Kotlin: **2.3.20**; Okio: **3.18.2**, selected by the new upstream dependency chain.
- Scala library: **2.13.9**, constrained in `glueMskRuntime` to fix `CVE-2022-36944`.
- Java target for packaging and focused tests: **17**; worker API fixtures: **Kafka 3.7.0**.
- Existing standard/Hive configurations keep their original Java target and contents.

The existing 19 consumer configurations have the same external module coordinates
as the preceding source graph. The four additional configurations are
`glueConverterArchive`, `glueMskRuntime`, `glueMskCompatibilityTestCompileClasspath`
and `glueMskCompatibilityTestRuntimeClasspath`. All 23 resolve without unresolved
dependencies or artifacts. The test configurations consume the exact packaged
graph as file dependencies, plus nontransitive worker API JARs and test libraries.

`glueConverterArchive` is private and nontransitive. A standard Gradle `Jar` task
retains all 29 converter-owned classes, including `org/apache/avro/Schemas.class`,
and the converter's own two Maven metadata files, byte-for-byte. It also preserves
LICENSE/NOTICE resources and supplies a deterministic manifest. No embedded Glue,
Wire, Jackson, Kafka or other fat-JAR implementations leak into the thin converter.
Glue common/serde and their resources are shipped separately.

`glueMskRuntime` retains `iceberg-aws` with separate SDK v2 modules. The AWS bundle,
Azure/GCP/Nessie bundles, old activation JAR, Kafka, SLF4J and MSK IAM library remain
excluded. SDK v1 core/STS/JMESPath and its CBOR dependency are no longer required.
Other dependency changes stay within Glue's migration chain: Wire, Kotlin,
Okio, code generators, Scala and its JSON-schema module. Joda-Time resolves to
2.10.2 instead of 2.12.7 after the SDK v1 requirement disappears.

The candidate contains **171 JARs**, versus 175 previously. Every JAR was inspected
for class versions, embedded Maven metadata, duplicate classes and nested
archives. There are no nested archives, no bundled Kafka/Connect, SLF4J or MSK IAM
classes, and no new duplicate class names. The **617 existing duplicates** remain
confined to unchanged Parquet JARs and four JSpecify annotation classes in the
patched Guava bundle. Glue/Wire introduce none. Base classes have maximum major
version **61**; multi-release entries selected by Java 17 also pass. Later Java
overrides remain in upstream multi-release JARs where present.

All **139 unaffected dependency JARs** have identical hashes, including the patched
Parquet Jackson and Iceberg Guava bundles. The three rebuilt connector JARs differ
only in generated manifests. Jackson, Netty, Hadoop, Guava and compression fixes
remain intact.

Compared directly with the earlier Glue 2.0.1 candidate, the Scala patch changes
only `scala-library:2.13.1` to **2.13.9** in the dependency graph and ZIP. All
**167 other dependency JARs** are byte-identical; only generated manifests change
in the three connector JARs. The thin converter hash is unchanged. The original
pre-Scala candidate and its verification outputs remain preserved separately.

Gradle's default distribution plugin adds every distribution to `assemble`.
The new archives are explicitly removed from that default artifact set; standard
`assemble`, `check`, `test`, `distZip` and `hiveDistZip` do not invoke Glue packaging
or compatibility tests. The custom source set is excluded from automatic
Checkstyle lifecycle selection. This was checked through task dependencies
without executing the application suite.

## Focused offline tests

Eight test cases pass, with no skipped cases, on Temurin **17.0.21+9** and Kafka
**3.7.0** APIs:

- Load the existing Avro converter class and String key converter; configure
  literal `region`, `registry.name`, `avroRecordType` and
  `schemaAutoRegistrationEnabled` keys and inspect their effective values.
- Verify `assumeRoleArn`, explicit/default `assumeRoleSessionName` and region reach
  the converter credentials factory, and both serde instances receive that
  provider. A separately configured converter retains default credentials.
- Decode a fixed message generated with Glue **1.1.27** and Avro **1.11.4**, asserting
  decimal, timestamp, null and nested fields. Serialize it back to identical bytes,
  round-trip a non-null optional field, and preserve tombstones in both directions.
  Only schema lookup is mocked; real Glue framing, Avro decoding and Connect
  conversion execute. Credential resolution is never invoked.
- Parse and render protobuf schemas with imported and nested messages through
  Glue's Wire/Okio integration.
- Decode upstream fixture `9b060a80ffffff0f9c06` through both byte-array and stream
  paths. Both throw checked `ProtocolException` with the upstream expected message:
  `Negative length: -128. Reader position: 8. Last read tag: 1.`
- Generate a schema from a Java object through Glue's Scala-backed JSON-schema
  generator, assert the resulting string/integer field types, serialize the object
  and validate its JSON against the generated schema. This exercises the actual
  Scala consumer on 2.13.9 without AWS calls.

The test fixtures and provenance are maintained in
[`src/glueMskCompatibilityTest/resources`](../../kafka-connect-runtime/src/glueMskCompatibilityTest/resources/README.md).
The role test replaces the protected credentials factory and does not validate
STS requests, live IAM trust, S3/catalog access or managed-worker class loading.
Deployment configuration and Iceberg credentials were not changed.

## Static linkage blocker

ASM inspection indexes the two extracted plugin distributions with the same Kafka
3.7.0 and SLF4J worker API fixtures. Targets are the union of **14,736 classes** from
every changed dependency JAR and Kafka API JAR. It checks class, method and field
references entering/leaving these libraries, including Glue, Wire, Kotlin, Okio,
Scala and code generators. JDK classes, inherited members and JVM signature
polymorphism are handled statically; application classes are not executed.

| Measurement | Pre-Glue baseline | Final candidate |
| --- | ---: | ---: |
| Indexed classes | 66,542 | 66,171 |
| Unresolved reference records | 4,033 | 7,695 |

The candidate introduces **5,013 reference records** absent from the previous
report, not 5,013 unique missing classes or demonstrated runtime failures:

- **5,011** originate in Kotlin scripting/compiler code and reference unavailable
  compiler types/members such as `CompilerConfiguration`, Kotlin IR/FIR and
  embedded IntelliJ classes. The scripting artifacts do not provide the full
  compiler implementation in this resolved graph.
- **Two** originate in `KotlinGenerator.generateRpcFunction` and reference
  `GrpcClientStreamingCall` and `GrpcServerStreamingCall`. Glue's upstream POM
  excludes `wire-grpc-client` from its compiler dependency.

The baseline already has incomplete optional/compiler paths, but the requirement
was zero newly unresolved references across changed-library boundaries. These
results fail that requirement. Passing the focused converter tests does not prove
all compiler/gRPC paths unreachable. No missing-reference allowlist, local Wire
adapter, compiler pruning or additional compiler runtime was used to force a pass.

Comparing the final candidate with the pre-Scala Glue candidate finds **identical
sets of unresolved references**: the Scala patch adds zero linkage errors.

## Scanner comparison

The pre-Glue baseline, pre-Scala Glue candidate and final candidate were scanned
from their exact extracted JAR bytes with Trivy **0.76.1**,
`rootfs --scanners vuln --pkg-types library --offline-scan`, with database updates
and telemetry disabled. No suppressions or severity filters were used.

| Input | SHA-256 |
| --- | --- |
| Trivy binary | `5c03401caf9d3cc04d9e0c7f3abd102e97518e8237465cce702162b605535e8d` |
| Vulnerability DB | `68917cddd951d3d29ab1e6bc4e1a33cf3bf160743d19276290b77afad13cfa98` |
| Java DB | `823874792b6e8300a0f7a19c4031771b818654efbcefdb700f90115ed1a040e2` |

These match the recorded D04 inputs. Database update times remain
2026-11-05 01:19:45.907162652 UTC and 2026-11-05 01:08:50.735678238 UTC respectively.

| Finding | Pre-Glue baseline | Before Scala patch | Final candidate |
| --- | ---: | ---: | ---: |
| D09 / Wire / `CVE-2026-45799` | 1 | 0 | 0 |
| Scala / `CVE-2022-36944` | 0 | 1 | 0 |
| Total dependency findings | **1** | **1** | **0** |

The candidate has 181 identified package records, including
`org.scala-lang:scala-library:2.13.9`. The Scala constraint replaces vulnerable
2.13.1 introduced through `mbknor-jackson-jsonschema_2.13:1.0.39`, clearing critical
`CVE-2022-36944`. Both zero-new-findings and zero-total-findings gates now pass,
without suppressions. Earlier patched components remain free of reported findings
in this comparison. The remaining acceptance blocker is static linkage.

## Reproduction and evidence

On a Java 17 build host, the maintained tasks require no local packaging recipe:

```sh
./gradlew :iceberg-kafka-connect-runtime:glueMskDistZip
./gradlew --offline :iceberg-kafka-connect-runtime:glueMskCompatibilityTest
```

Populate the dependency cache before using `--offline`. The focused tests use
mocks/local fixtures and make no AWS calls; the build can download Maven inputs.
The normal ZIP destination is `kafka-connect-runtime/build/distributions/`.

The initial verification redirected outputs and copied the preceding
Gradle/scanner caches into ignored `build/glue-wire-verification/`. Scala patch
outputs are isolated under its `scala/` subdirectory and reuse the same caches,
scanner, databases and existing JDK:

```sh
export JAVA_HOME="$PWD/build/netty-verification/jdk17/Contents/Home"
export GRADLE_USER_HOME="$PWD/build/glue-wire-verification/gradle"

./gradlew --offline --no-daemon --console=plain \
  --project-cache-dir build/glue-wire-verification/scala/project-cache \
  -I build/glue-wire-verification/scala/verification.init.gradle \
  inspectGlueWireResolution \
  :iceberg-kafka-connect-runtime:glueMskCompatibilityTest \
  :iceberg-kafka-connect-runtime:glueMskDistZip

build/glue-wire-verification/tools/trivy rootfs \
  --cache-dir build/glue-wire-verification/trivy-cache \
  --scanners vuln --pkg-types library --offline-scan \
  --skip-db-update --skip-java-db-update --disable-telemetry --skip-version-check \
  --list-all-pkgs --format json \
  --output build/glue-wire-verification/scala/candidate-trivy.json \
  build/glue-wire-verification/scala/scan-input/candidate
```

Final evidence under `build/glue-wire-verification/scala/` includes
`candidate-resolution.json`, `dependency-changes.json`,
`candidate-artifact.json`, `candidate-jar-inventory.json`, `duplicate-classes.json`,
the JUnit XML reports, `build.log` (including opt-in verification),
`thin-reproducibility.json`, `linkage.log`, all three linkage-error files, all three
Trivy reports, `scala-dependency-changes.json`, `scala-artifact-changes.json` and
`acceptance-checks.json`. Temporary inspection scripts and downloaded upstream
sources remain ignored under that directory. Preserve these local files separately
for an exact handoff. No historical verification output was overwritten.

## Candidate identity

Built from working-tree changes on `1ee87513bfc69efee2e2ee3097f45e11afa21df3`, using
Gradle 8.1.1. This is a **blocked verification candidate**, not an accepted release.

- ZIP: [`iceberg-kafka-connect-runtime-glueMsk-0.1.0-dev.336.uncommitted+apl.3186.update.jackson.dependency.1ee8751.zip`](../../build/glue-wire-verification/scala/distributions/iceberg-kafka-connect-runtime-glueMsk-0.1.0-dev.336.uncommitted+apl.3186.update.jackson.dependency.1ee8751.zip)
- Size: **111,926,579 bytes**, containing **171 JARs**.
- ZIP SHA-256: `2a7ba068051fe39f8949bdf1b0bce54be1972f1fd2454ac25398a615ab3c61b3`.

The thin converter has reproducible SHA-256
`3df1d52f9c888135e5f20d19ac5aa45f74d8ede8104a4d10adcd4c1cbe5d2f4f`.
Its forced rebuild is byte-identical; the complete ZIP is not asserted reproducible
because the connector manifests contain generated build metadata.
