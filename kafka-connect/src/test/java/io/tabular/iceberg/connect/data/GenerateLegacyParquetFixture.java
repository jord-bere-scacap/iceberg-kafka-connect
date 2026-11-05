/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.tabular.iceberg.connect.data;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Files;
import org.apache.iceberg.IcebergBuild;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.types.Types;
import org.apache.parquet.Version;

/** Generates the checked-in legacy fixture only when explicitly run with the original writer. */
public class GenerateLegacyParquetFixture {

  static final Schema SCHEMA =
      new Schema(
          Types.NestedField.required(1, "id", Types.LongType.get()),
          Types.NestedField.optional(2, "note", Types.StringType.get()),
          Types.NestedField.optional(3, "amount", Types.DecimalType.of(18, 4)),
          Types.NestedField.optional(4, "local_time", Types.TimestampType.withoutZone()),
          Types.NestedField.optional(5, "utc_time", Types.TimestampType.withZone()),
          Types.NestedField.optional(
              6,
              "details",
              Types.StructType.of(
                  Types.NestedField.optional(7, "label", Types.StringType.get()),
                  Types.NestedField.optional(8, "price", Types.DecimalType.of(9, 2)))),
          Types.NestedField.optional(9, "large_amount", Types.DecimalType.of(25, 6)));

  static List<Record> rows() {
    Record first = GenericRecord.create(SCHEMA);
    first.setField("id", 101L);
    first.setField("note", "legacy and upgraded writers");
    first.setField("amount", new BigDecimal("12345678901234.5678"));
    first.setField("local_time", LocalDateTime.parse("2024-02-29T23:59:58.123456"));
    first.setField("utc_time", OffsetDateTime.parse("2024-02-29T21:59:58.654321Z"));
    first.setField(
        "details",
        GenericRecord.create(SCHEMA.findType("details").asStructType())
            .copy("label", "nested value", "price", new BigDecimal("1234567.89")));
    first.setField("large_amount", new BigDecimal("1234567890123456789.123456"));

    Record nulls = GenericRecord.create(SCHEMA);
    nulls.setField("id", 102L);

    Record negative = GenericRecord.create(SCHEMA);
    negative.setField("id", 103L);
    negative.setField("note", "");
    negative.setField("amount", new BigDecimal("-0.0001"));
    negative.setField("local_time", LocalDateTime.parse("1960-01-02T03:04:05.000001"));
    negative.setField("utc_time", OffsetDateTime.parse("1960-01-02T03:04:05.999999Z"));
    negative.setField(
        "details",
        GenericRecord.create(SCHEMA.findType("details").asStructType())
            .copy("label", null, "price", new BigDecimal("-0.01")));
    negative.setField("large_amount", new BigDecimal("-1234567890123456789.123456"));
    return ImmutableList.of(first, nulls, negative);
  }

  public static void main(String[] args) throws IOException, ReflectiveOperationException {
    // Read the runtime version instead of a constant inlined by the compiler.
    String parquetVersion = (String) Version.class.getField("VERSION_NUMBER").get(null);
    if (!"1.13.1".equals(parquetVersion) || !"1.5.2".equals(IcebergBuild.version())) {
      throw new IllegalStateException("Fixture generation requires Iceberg 1.5.2 / Parquet 1.13.1");
    }
    if (args.length != 1 || new File(args[0]).exists()) {
      throw new IllegalArgumentException(
          "Supply one new output path; existing fixtures are preserved");
    }
    try (FileAppender<Record> writer =
        new GenericAppenderFactory(SCHEMA)
            .newAppender(Files.localOutput(new File(args[0])), FileFormat.PARQUET)) {
      writer.addAll(rows());
    }
    System.out.println(
        "Generated with " + IcebergBuild.fullVersion() + " / Parquet " + parquetVersion);
  }
}
