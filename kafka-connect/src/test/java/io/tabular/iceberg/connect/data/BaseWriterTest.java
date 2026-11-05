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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.tabular.iceberg.connect.IcebergSinkConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.FileContent;
import org.apache.iceberg.LocationProviders;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetReaders;
import org.apache.iceberg.encryption.PlaintextEncryptionManager;
import org.apache.iceberg.inmemory.InMemoryFileIO;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.TaskWriter;
import org.apache.iceberg.io.WriteResult;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.BeforeEach;

public class BaseWriterTest {

  protected InMemoryFileIO fileIO;
  protected Table table;

  protected static final Schema SCHEMA =
      new Schema(
          ImmutableList.of(
              Types.NestedField.required(1, "id", Types.LongType.get()),
              Types.NestedField.required(2, "data", Types.StringType.get()),
              Types.NestedField.required(3, "id2", Types.LongType.get())),
          ImmutableSet.of(1, 3));

  protected static final PartitionSpec SPEC =
      PartitionSpec.builderFor(SCHEMA).identity("data").build();

  @BeforeEach
  public void before() {
    fileIO = new InMemoryFileIO();

    table = mock(Table.class);
    when(table.schema()).thenReturn(SCHEMA);
    when(table.spec()).thenReturn(PartitionSpec.unpartitioned());
    when(table.io()).thenReturn(fileIO);
    when(table.locationProvider())
        .thenReturn(LocationProviders.locationsFor("file", ImmutableMap.of()));
    when(table.encryption()).thenReturn(new PlaintextEncryptionManager());
    when(table.properties()).thenReturn(ImmutableMap.of());
  }

  protected WriteResult writeTest(
      List<Record> rows, IcebergSinkConfig config, Class<?> expectedWriterClass) {
    try (TaskWriter<Record> writer = Utilities.createTableWriter(table, "name", config)) {
      assertThat(writer.getClass()).isEqualTo(expectedWriterClass);

      rows.forEach(
          row -> {
            try {
              writer.write(row);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });

      return writer.complete();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  protected List<Record> readParquetFiles(ContentFile<?>[] files, Schema projection) {
    List<Record> rows = new ArrayList<>();
    for (ContentFile<?> file : files) {
      rows.addAll(readParquet(fileIO.newInputFile(file.path().toString()), projection));
    }
    return rows;
  }

  protected static List<Record> readParquet(InputFile input, Schema projection) {
    try (CloseableIterable<Record> reader =
        Parquet.read(input)
            .project(projection)
            .createReaderFunc(
                fileSchema -> GenericParquetReaders.buildReader(projection, fileSchema))
            .build()) {
      return ImmutableList.copyOf(reader);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  protected void assertParquetDeletes(WriteResult result, List<Record> rows) {
    Schema deleteSchema = SCHEMA.select("id", "id2");
    List<Record> expectedKeys = new ArrayList<>();
    for (Record row : rows) {
      expectedKeys.add(
          GenericRecord.create(deleteSchema)
              .copy("id", row.getField("id"), "id2", row.getField("id2")));
    }
    assertThat(result.deleteFiles())
        .allSatisfy(
            file -> {
              assertThat(file.content()).isEqualTo(FileContent.EQUALITY_DELETES);
              assertThat(file.equalityFieldIds()).containsExactlyInAnyOrder(1, 3);
            });
    assertThat(readParquetFiles(result.deleteFiles(), deleteSchema))
        .containsExactlyInAnyOrderElementsOf(expectedKeys);
  }
}
