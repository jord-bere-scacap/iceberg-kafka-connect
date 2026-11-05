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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.tabular.iceberg.connect.IcebergSinkConfig;
import io.tabular.iceberg.connect.TableSinkConfig;
import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import org.apache.iceberg.Files;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.UnpartitionedWriter;
import org.apache.iceberg.io.WriteResult;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ParquetCompatibilityTest extends BaseWriterTest {

  private static final Schema COMPATIBILITY_SCHEMA = GenerateLegacyParquetFixture.SCHEMA;
  private static final Schema PROJECTION =
      new Schema(
          Types.NestedField.optional(3, "total", Types.DecimalType.of(18, 4)),
          Types.NestedField.required(1, "record_id", Types.LongType.get()),
          Types.NestedField.optional(
              6,
              "details",
              Types.StructType.of(Types.NestedField.optional(7, "name", Types.StringType.get()))));

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void testRoundTripAndProjection(boolean partitioned) {
    when(table.schema()).thenReturn(COMPATIBILITY_SCHEMA);
    when(table.spec())
        .thenReturn(
            partitioned
                ? PartitionSpec.builderFor(COMPATIBILITY_SCHEMA).identity("id").build()
                : PartitionSpec.unpartitioned());
    IcebergSinkConfig config = mock(IcebergSinkConfig.class);
    when(config.tableConfig(any())).thenReturn(mock(TableSinkConfig.class));
    when(config.writeProps()).thenReturn(ImmutableMap.of("write.format.default", "parquet"));

    List<Record> expected = GenerateLegacyParquetFixture.rows();
    WriteResult result =
        writeTest(
            expected,
            config,
            partitioned ? PartitionedAppendWriter.class : UnpartitionedWriter.class);

    assertThat(result.dataFiles()).hasSize(partitioned ? 3 : 1);
    assertThat(readParquetFiles(result.dataFiles(), COMPATIBILITY_SCHEMA))
        .containsExactlyInAnyOrderElementsOf(expected);
    assertThat(readParquetFiles(result.dataFiles(), PROJECTION))
        .containsExactlyInAnyOrderElementsOf(projectedRows());
  }

  @Test
  public void testReadLegacyFile() throws URISyntaxException {
    assertThat(readParquet(Files.localInput(legacyFile()), COMPATIBILITY_SCHEMA))
        .containsExactlyElementsOf(GenerateLegacyParquetFixture.rows());
  }

  @Test
  public void testProjectLegacyFile() throws URISyntaxException {
    assertThat(readParquet(Files.localInput(legacyFile()), PROJECTION))
        .containsExactlyElementsOf(projectedRows());
  }

  private File legacyFile() throws URISyntaxException {
    return new File(
        getClass().getResource("/parquet/iceberg-1.5.2-parquet-1.13.1.parquet").toURI());
  }

  private List<Record> projectedRows() {
    List<Record> rows = GenerateLegacyParquetFixture.rows();
    Record first = GenericRecord.create(PROJECTION);
    first.setField("total", rows.get(0).getField("amount"));
    first.setField("record_id", 101L);
    first.setField(
        "details",
        GenericRecord.create(PROJECTION.findType("details").asStructType())
            .copy("name", "nested value"));

    Record nulls = GenericRecord.create(PROJECTION);
    nulls.setField("record_id", 102L);

    Record negative = GenericRecord.create(PROJECTION);
    negative.setField("total", rows.get(2).getField("amount"));
    negative.setField("record_id", 103L);
    negative.setField(
        "details", GenericRecord.create(PROJECTION.findType("details").asStructType()));
    return ImmutableList.of(first, nulls, negative);
  }
}
