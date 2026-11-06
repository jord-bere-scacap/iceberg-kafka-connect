# D09: Glue 2.0.1 / Wire 7.0.1 candidate — acceptance blocked

## Copyable PR description

The previous Glue/MSK distribution contains Wire 5.2.0 and one D09 finding,
`CVE-2026-45799`. Pin Glue 2.0.1, Wire 7.0.1 and AWS SDK v2 BOM 2.32.25, and move
the previously local Glue/MSK packaging recipe into an opt-in `glueMskDistZip`
distribution. Existing standard/Hive dependency graphs and prior security
overrides remain unchanged.

Constrain Glue's Scala library to patched **2.13.9** to fix `CVE-2022-36944`,
introduced by its Scala-backed JSON-schema generator. The constraint applies only
to `glueMskRuntime`; existing standard/Hive graphs are unaffected.

Build a deterministic thin converter JAR from the published Glue fat JAR, keeping
its converter classes, Avro helper, Maven metadata and attribution. Resolve Glue
common/serde and their dependency chain separately for Java 17. Retain Iceberg's
AWS integration and exclude worker-provided Kafka, SLF4J and MSK IAM libraries.
SDK v1 disappears naturally from the new graph. No application Java, deployment
configuration or credential routing changes are included.

Add an explicitly invoked `glueMskCompatibilityTest` task using Java 17 and Kafka
3.7.0 APIs. All eight offline cases pass: existing converter property names,
role/session/region forwarding, default credentials, legacy Avro framing,
decimal/timestamp/nullable/nested values, tombstones, Glue protobuf schema handling,
the upstream Wire negative-length regression through stream and byte-array
decoding, and Glue's Scala-backed JSON-schema generation/validation. Role forwarding
uses an overridden credentials factory; it does not test
an actual STS exchange or managed-worker behavior.

**The zero-finding scan passes; release acceptance remains blocked by static linkage.**

- Trivy 0.76.1 with the recorded databases reports **1 → 0 findings**. Both D09 and
  critical Scala `CVE-2022-36944` are absent, with zero new findings and no
  suppressions. Scala 2.13.9 is explicitly identified in the scanned ZIP.
- Static class/method/field inspection reports **5,013 newly unresolved reference
  records**: 5,011 in Kotlin scripting/compiler paths and two in Wire's Kotlin
  gRPC generator. These results are unchanged by the Scala patch, which introduces
  zero additional linkage errors. The offline tests do not establish that those
  paths are unreachable. No local Wire adapter or dependency pruning was introduced.
- The 19 existing consumer graphs are unchanged; all 23 inspected configurations
  resolve without errors. All 171 candidate JARs were inspected. The 139 unaffected
  dependency JARs, including earlier patched bundles, are byte-identical; the three
  connector JARs differ only in manifests. There are no new duplicate class names,
  embedded archives, or worker-provided implementations. Effective bytecode is
  compatible with Java 17. The thin converter rebuild is byte-identical.
- Compared with the preceding Glue 2.0.1 candidate, Scala 2.13.1 → 2.13.9 is the only
  dependency change; all **167 other dependency JARs** remain byte-identical.

Follow-up needs a decision on the incomplete compiler/gRPC dependency paths,
followed by the same graph, linkage, offline test
and scanner checks. No deployment, live MSK/AWS smoke test, GitHub checks or PR
creation was performed.

[Detailed results, reproduction commands and candidate identity](GLUE_WIRE_VERIFICATION.md).
