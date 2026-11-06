`order-glue-1.1.27.hex` is a fixed, uncompressed Glue Schema Registry frame generated
with the preceding distribution's Glue 1.1.27 `SerializationDataEncoder` and Avro
1.11.4 `GenericDatumWriter`, using `order.avsc`. Its schema version UUID is
`00112233-4455-6677-8899-aabbccddeeff`. The record contains `order-1`, decimal
`123.4567`, timestamp `1700000000123` milliseconds, a null note, and customer
`{name: Ada, active: true}`. The test supplies the schema through a mocked registry
client and checks that Glue 2.0.1 produces identical framed bytes.

The negative-length protobuf bytes and expected checked exception message come
from Wire 7.0.1's
[`ProtoReaderTest.testSkipGroupRejectsNegativeLength`](https://github.com/square/wire/blob/7.0.1/wire-runtime/src/commonTest/kotlin/com/squareup/wire/ProtoReaderTest.kt)
and
[`ProtoReader32Test.testSkipGroupRejectsNegativeLength`](https://github.com/square/wire/blob/7.0.1/wire-runtime/src/commonTest/kotlin/com/squareup/wire/ProtoReader32Test.kt).
They are exercised through both stream and byte-array decoding using Wire's
empty-message adapter, which skips the same unknown group.
