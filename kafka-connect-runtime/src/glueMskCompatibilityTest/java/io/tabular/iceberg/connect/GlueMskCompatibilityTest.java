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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.services.schemaregistry.common.AWSSchemaRegistryClient;
import com.amazonaws.services.schemaregistry.common.configs.GlueSchemaRegistryConfiguration;
import com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryDeserializationFacade;
import com.amazonaws.services.schemaregistry.kafkaconnect.AWSKafkaAvroConverter;
import com.amazonaws.services.schemaregistry.serializers.avro.AWSKafkaAvroSerializer;
import com.amazonaws.services.schemaregistry.utils.AvroRecordType;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.utils.AppInfoParser;
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Timestamp;
import org.apache.kafka.connect.storage.Converter;
import org.apache.kafka.connect.storage.StringConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.glue.model.DataFormat;
import software.amazon.awssdk.services.glue.model.GetSchemaVersionResponse;

class GlueMskCompatibilityTest {
  private static final String TOPIC = "orders";
  private static final UUID SCHEMA_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

  @Test
  void loadsExistingConvertersWithWorkerApisAndDefaultCredentials() throws Exception {
    assertEquals(17, Runtime.version().feature());
    assertEquals("3.7.0", AppInfoParser.getVersion());
    Converter loaded =
        (Converter)
            Class.forName(
                    "com.amazonaws.services.schemaregistry.kafkaconnect.AWSKafkaAvroConverter")
                .getDeclaredConstructor()
                .newInstance();
    loaded.configure(config(), false);
    AWSKafkaAvroConverter converter = assertInstanceOf(AWSKafkaAvroConverter.class, loaded);
    assertFalse(converter.isKey());
    assertInstanceOf(
        DefaultCredentialsProvider.class, converter.getDeserializer().getCredentialProvider());
    assertInstanceOf(
        DefaultCredentialsProvider.class, converter.getSerializer().getCredentialProvider());
    assertConfiguration(converter);
    assertSame(SchemaAndValue.NULL, converter.toConnectData(TOPIC, null));
    assertNull(converter.fromConnectData(TOPIC, null, null));

    try (StringConverter key = new StringConverter()) {
      key.configure(Map.of(), true);
      byte[] bytes = "order-1".getBytes(StandardCharsets.UTF_8);
      assertEquals("order-1", key.toConnectData(TOPIC, bytes).value());
      assertArrayEquals(bytes, key.fromConnectData(TOPIC, null, "order-1"));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void forwardsRoleRegionAndExplicitOrDefaultSessionOnlyToConverter(boolean explicitSession) {
    Map<String, Object> properties = new HashMap<>(config());
    String role = "arn:aws:iam::123456789012:role/schema-registry-reader";
    properties.put("assumeRoleArn", role);
    if (explicitSession) {
      properties.put("assumeRoleSessionName", "schema-reader-session");
    }
    RoleCapturingConverter converter = new RoleCapturingConverter();
    converter.configure(properties, false);
    assertEquals(
        List.of(
            role,
            explicitSession ? "schema-reader-session" : "kafka-connect-session",
            "eu-central-1"),
        converter.roleArguments);
    assertSame(converter.credentials, converter.getSerializer().getCredentialProvider());
    assertSame(converter.credentials, converter.getDeserializer().getCredentialProvider());
    assertConfiguration(converter);
    assertSame(SchemaAndValue.NULL, converter.toConnectData(TOPIC, null));
    verifyNoInteractions(converter.credentials);

    AWSKafkaAvroConverter independent = new AWSKafkaAvroConverter();
    independent.configure(config(), false);
    assertInstanceOf(
        DefaultCredentialsProvider.class, independent.getDeserializer().getCredentialProvider());
  }

  @Test
  void decodesLegacyFramingAndRoundTripsLogicalNullableAndNestedFields() throws IOException {
    String schema = resource("/order.avsc");
    byte[] legacyFrame = HexFormat.of().parseHex(resource("/order-glue-1.1.27.hex").trim());
    AwsCredentialsProvider credentials = mock(AwsCredentialsProvider.class);
    AWSSchemaRegistryClient registry = mock(AWSSchemaRegistryClient.class);
    when(registry.getSchemaVersionResponse(SCHEMA_ID.toString()))
        .thenReturn(
            GetSchemaVersionResponse.builder()
                .schemaDefinition(schema)
                .dataFormat(DataFormat.AVRO)
                .schemaArn("arn:aws:glue:eu-central-1:123456789012:schema/offline-registry/Order")
                .build());
    AWSKafkaAvroConverter converter = new AWSKafkaAvroConverter();
    converter.configure(config(), false);
    converter
        .getDeserializer()
        .setGlueSchemaRegistryDeserializationFacade(
            GlueSchemaRegistryDeserializationFacade.builder()
                .configs(config())
                .credentialProvider(credentials)
                .schemaRegistryClient(registry)
                .build());
    // A fixed schema ID bypasses registration while exercising the real serializer and encoder.
    converter.setSerializer(new AWSKafkaAvroSerializer(credentials, SCHEMA_ID, config()));

    SchemaAndValue decoded = converter.toConnectData(TOPIC, legacyFrame);
    Struct order = assertInstanceOf(Struct.class, decoded.value());
    assertEquals("order-1", order.getString("id"));
    assertEquals(new BigDecimal("123.4567"), order.get("amount"));
    assertEquals(Decimal.LOGICAL_NAME, order.schema().field("amount").schema().name());
    assertEquals(new Date(1700000000123L), order.get("created_at"));
    assertEquals(Timestamp.LOGICAL_NAME, order.schema().field("created_at").schema().name());
    assertNull(order.get("note"));
    assertEquals("Ada", order.getStruct("customer").getString("name"));
    assertEquals(true, order.getStruct("customer").getBoolean("active"));
    assertArrayEquals(legacyFrame, converter.fromConnectData(TOPIC, decoded.schema(), order));

    order.put("note", "present");
    byte[] updated = converter.fromConnectData(TOPIC, decoded.schema(), order);
    assertEquals(order, converter.toConnectData(TOPIC, updated).value());
    assertSame(SchemaAndValue.NULL, converter.toConnectData(TOPIC, null));
    assertNull(converter.fromConnectData(TOPIC, null, null));
    verify(registry).getSchemaVersionResponse(SCHEMA_ID.toString());
    verifyNoMoreInteractions(registry);
    verifyNoInteractions(credentials);
  }

  private static Map<String, Object> config() {
    return Map.of(
        "region", "eu-central-1",
        "registry.name", "offline-registry",
        "avroRecordType", "GENERIC_RECORD",
        "schemaAutoRegistrationEnabled", false);
  }

  private static void assertConfiguration(AWSKafkaAvroConverter converter) {
    GlueSchemaRegistryConfiguration effective =
        converter
            .getDeserializer()
            .getGlueSchemaRegistryDeserializationFacade()
            .getGlueSchemaRegistryConfiguration();
    assertEquals("eu-central-1", effective.getRegion());
    assertEquals("offline-registry", effective.getRegistryName());
    assertEquals(AvroRecordType.GENERIC_RECORD, effective.getAvroRecordType());
    assertFalse(effective.isSchemaAutoRegistrationEnabled());
  }

  private static String resource(String name) throws IOException {
    try (InputStream stream = GlueMskCompatibilityTest.class.getResourceAsStream(name)) {
      if (stream == null) {
        throw new IOException("Missing compatibility fixture: " + name);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static class RoleCapturingConverter extends AWSKafkaAvroConverter {
    private final AwsCredentialsProvider credentials = mock(AwsCredentialsProvider.class);
    private List<String> roleArguments;

    @Override
    protected AwsCredentialsProvider getCredentialsProvider(
        String roleArn, String sessionName, String region) {
      roleArguments = List.of(roleArn, sessionName, region);
      return credentials;
    }
  }
}
