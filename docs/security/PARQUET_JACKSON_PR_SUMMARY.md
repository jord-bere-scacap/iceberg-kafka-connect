# D04: patch Parquet's embedded Jackson

## Copyable PR description

Parquet 1.15.2 embeds Jackson 2.18.1, which still produces 17 D04 package/advisory matches after the earlier dependency fixes. Replace that bundle with `parquet-jackson-patched-2.18.11.jar`, built locally from Jackson core, databind and annotations using the existing Jackson BOM and Shadow 8.1.1. Gradle substitutes this project for `org.apache.parquet:parquet-jackson`; all other Parquet modules remain at 1.15.2 and Iceberg remains at 1.5.2.

The bundle preserves Parquet's `shaded.parquet.com.fasterxml.jackson` namespace, all 1,073 base classes, Maven identification metadata, licenses/notices and both relocated service providers. Its inputs stay private, and consumers receive the shaded JAR for compilation and runtime. Signatures, module descriptors and versioned overrides are excluded, matching the existing Parquet bundle's base-only loading. Every omitted override has a Java 8 compatible base counterpart. No original Parquet bundle is used as a build input, and no connector API, configuration or application Java source changes are included.

Verification on 2026-11-06:

- Both the source graph and the preceding Glue/MSK packaging recipe resolve all 19 consumer configurations without errors. Exactly five configurations receive one replacement each. Inputs and outgoing variants pass inspection.
- Compilation and a fresh Glue/MSK `distZip` pass. All 175 packaged JARs were inspected: one replacement, no original bundle, and all three embedded Jackson components at 2.18.11.
- Trivy 0.76.1, with byte-identical recorded vulnerability and Java databases, reports **18 → 1 findings**: **zero D04 findings and zero new findings**. The 14 historical D04 matches and 17 current D04 matches are reconciled separately.
- Static class/method/field inspection across bundle boundaries, including Parquet consumers, reports **zero unresolved references and zero introduced errors**. A forced bundle rebuild produces the same SHA-256.
- All **171 other dependency JARs remain byte-identical**, including earlier fixes and AWS's separately embedded Jackson. The three rebuilt connector JARs differ only in generated manifests. Historical evidence and the original assessment remain preserved.

No application or smoke tests, GitHub checks, deployment or PR creation were performed. Runtime compatibility remains unverified. The remaining finding is **Wire/D09**, `wire-runtime-jvm:5.2.0` / `CVE-2026-45799`.

## Candidate identity

Built from working-tree changes on `5014a2d6ad6741c541f603d5ed9e37c1cff6fe56`, using Gradle 8.1.1 and Temurin JDK 17.0.21+9.

- ZIP: [`build/parquet-jackson-verification/distributions/iceberg-kafka-connect-runtime-0.1.0-dev.334.uncommitted+apl.3186.update.jackson.dependency.5014a2d.zip`](../../build/parquet-jackson-verification/distributions/iceberg-kafka-connect-runtime-0.1.0-dev.334.uncommitted+apl.3186.update.jackson.dependency.5014a2d.zip)
- ZIP size: **111,680,963 bytes**, containing **175 JARs**.
- ZIP SHA-256: `3dd22cd6ec73db7c2e988091496b644a45a8b280fbe78e3830a232bb04d0f9ed`
- Bundle SHA-256: `4acc5afa2086d1d00468a70f99de54c2f44dda241c8e5eb033d4a53f710ed78a`

## Ongoing maintenance

This is a locally maintained replacement, not an upstream Parquet release. Keep its version tied to the catalog's Jackson BOM and repeat class/resource coverage, reproducibility, consumer linkage and distribution scans whenever Jackson, Parquet or Shadow changes. Continue preserving Jackson and embedded third-party attribution. Reassess the base-only packaging decision when changing Jackson versions or the supported Java baseline. Remove the substitution when an acceptable upstream bundle can replace it, after repeating the same verification.

[Detailed verification and reproduction notes](PARQUET_JACKSON_VERIFICATION.md) retain the historical/current advisory distinction. Full machine-readable evidence and the candidate are local, ignored build outputs; archive them separately for handoff. The Glue/MSK packaging recipe and converter artifacts remain local verification inputs, as in the preceding compression verification.
