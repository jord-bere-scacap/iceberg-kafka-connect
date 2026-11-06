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
package io.tabular.iceberg.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.schemaregistry.deserializers.protobuf.ProtobufSchemaParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.squareup.wire.ProtoAdapter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WireCompatibilityTest {
  @Test
  void glueLoadsAndConvertsProtobufSchemasWithImportsAndNestedMessages() throws Exception {
    String schema =
        "syntax = \"proto3\"; package compatibility;"
            + "import \"google/protobuf/timestamp.proto\";"
            + "message Order { string id = 1; google.protobuf.Timestamp created_at = 2;"
            + "message Customer { string name = 1; } Customer customer = 3; }";
    Descriptors.FileDescriptor file = ProtobufSchemaParser.parse(schema, "order.proto");
    Descriptors.Descriptor order = file.findMessageTypeByName("Order");
    assertEquals(
        "google.protobuf.Timestamp",
        order.findFieldByName("created_at").getMessageType().getFullName());
    assertEquals(
        "compatibility.Order.Customer",
        order.findFieldByName("customer").getMessageType().getFullName());
    DynamicMessage message =
        DynamicMessage.newBuilder(order).setField(order.findFieldByName("id"), "order-1").build();
    assertEquals(message, DynamicMessage.parseFrom(order, message.toByteArray()));
    String rendered =
        com.amazonaws.services.schemaregistry.utils.ProtobufSchemaParser
            .getProtobufSchemaStringFromFileDescriptorProto(file.toProto());
    assertEquals(
        order.toProto(),
        ProtobufSchemaParser.parse(rendered, "order.proto")
            .findMessageTypeByName("Order")
            .toProto());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsUpstreamNegativeLengthInSkippedGroupWithCheckedException(boolean stream)
      throws IOException {
    // Wire 7.0.1 ProtoReaderTest / ProtoReader32Test.testSkipGroupRejectsNegativeLength.
    byte[] malformed = HexFormat.of().parseHex("9b060a80ffffff0f9c06");
    ProtocolException error =
        assertThrows(
            ProtocolException.class,
            () -> {
              if (stream) {
                ProtoAdapter.EMPTY.decode(new ByteArrayInputStream(malformed));
              } else {
                ProtoAdapter.EMPTY.decode(malformed);
              }
            });
    assertEquals(
        "Negative length: -128. Reader position: 8. Last read tag: 1.", error.getMessage());
  }
}
