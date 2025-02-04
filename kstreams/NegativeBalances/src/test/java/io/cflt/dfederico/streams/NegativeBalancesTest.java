package io.cflt.dfederico.streams;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

@Slf4j
public class NegativeBalancesTest {

    private static final String inputTopic = "inputTopic";
    private static final String outputTopic = "outputTopic";
    // A mocked schema registry for our serdes to use
    private static final String SCHEMA_REGISTRY_SCOPE = NegativeBalancesTest.class.getName();
    private static final String MOCK_SCHEMA_REGISTRY_URL = "mock://" + SCHEMA_REGISTRY_SCOPE;

    private static String avroSchema = "{\n" +
            "  \"connect.name\": \"cdc3.accounts.account_balances.Value\",\n" +
            "  \"fields\": [\n" +
            "    {\n" +
            "      \"name\": \"id\",\n" +
            "      \"type\": \"int\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"name\": \"customer_id\",\n" +
            "      \"type\": \"int\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"default\": null,\n" +
            "      \"name\": \"balance\",\n" +
            "      \"type\": [\n" +
            "        \"null\",\n" +
            "        \"double\"\n" +
            "      ]\n" +
            "    }\n" +
            "  ],\n" +
            "  \"name\": \"Balances\",\n" +
            "  \"namespace\": \"io.cflt.dfederico.balances\",\n" +
            "  \"type\": \"record\"\n" +
            "}";

    @BeforeEach
    public void setUp() throws IOException {
    }

    @Test
    public void SimpleTest() throws RestClientException, IOException {
        final Properties properties = new Properties();
        properties.put("application.id", "balance-notification-test");
        properties.put("bootstrap.servers", "dummy.kafka.confluent.cloud:9092");
        properties.put("default.topic.replication.factor", "1");
        properties.put("offset.reset.policy", "earliest");
        properties.put("state.dir", "/tmp/kafka-streams");

//        final Schema schema = new Schema.Parser().parse(
//                getClass().getResourceAsStream("balances.mock.avsc")
//        );
        final Schema schema = new Schema.Parser().parse(avroSchema);

        final SchemaRegistryClient schemaRegistryClient = MockSchemaRegistry.getClientForScope(SCHEMA_REGISTRY_SCOPE);
        schemaRegistryClient.register("inputTopic-value", new AvroSchema(schema));

        final GenericRecord record = new GenericData.Record(schema);
        record.put("id", 1234);
        record.put("customer_id", 4321);
        record.put("balance", 123.45d);
        final List<Object> inputValues = Collections.singletonList(record);

        //
        // Step 1: Configure and start the processor topology.
        //
        final StreamsBuilder builder = new StreamsBuilder();

        final Properties streamsConfiguration = new Properties();
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "generic-avro-integration-test");
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy config");
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, GenericAvroSerde.class);
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, GenericAvroSerde.class);
        streamsConfiguration.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_SCHEMA_REGISTRY_URL);

        // Write the input data as-is to the output topic.
        //   builder.stream(inputTopic).to(outputTopic);
        //
        // However, in the code below we intentionally override the default serdes in `to()` to
        // demonstrate how you can construct and configure a generic Avro serde manually.
        final Serde<String> stringSerde = Serdes.String();
        final Serde<GenericRecord> genericAvroSerde = new GenericAvroSerde();
        // Note how we must manually call `configure()` on this serde to configure the schema registry.
        // This is different from the case of setting default serdes (see `streamsConfiguration`
        // above), which will be auto-configured based on the `StreamsConfiguration` instance.
        genericAvroSerde.configure(
                Collections.singletonMap(AbstractKafkaSchemaSerDeConfig .SCHEMA_REGISTRY_URL_CONFIG, MOCK_SCHEMA_REGISTRY_URL),
                /*isKey*/ false);
        final KStream<String, GenericRecord> stream = builder.stream(inputTopic);
        stream.to(outputTopic, Produced.with(stringSerde, genericAvroSerde));

        try (final TopologyTestDriver topologyTestDriver = new TopologyTestDriver(builder.build(), streamsConfiguration)){
            //
            // Step 2: Setup input and output topics.
            //
            final TestInputTopic<Void, Object> input = topologyTestDriver
                    .createInputTopic(inputTopic,
                            new NothingSerde<>(),
                            new KafkaAvroSerializer(schemaRegistryClient));

            final TestOutputTopic<Void, Object> output = topologyTestDriver
                    .createOutputTopic(outputTopic,
                            new NothingSerde<>(),
                            new KafkaAvroDeserializer(schemaRegistryClient));

            //
            // Step 3: Produce some input data to the input topic.
            //
            input.pipeValueList(inputValues);

            //
            // Step 4: Verify the application's output data.
            //
            assertThat(output.readValuesToList(), equalTo(inputValues));
        } finally {
            MockSchemaRegistry.dropScope(SCHEMA_REGISTRY_SCOPE);
        }
    }
//    @Test
//    public void validateIfTestDriverCreated() {
//        assertThat(testDriver).isNotNull();
//    }

//    @Test
//    public void validateHappyEnrichment() throws JsonProcessingException {
//
//        final String sensorRecord = "{\"source\":\"source3\",\"key\":\"type1:imei00000001\",\"type\":\"test\",\"event\":{\"timestamp\":\"2024-11-11T10:50:00Z\",\"sensorId\":\"123_3\",\"sensorType\":\"1000\", \"sensorOwner\": \"leidson\"}}";
//        final String sourceRecord = "{\"id\":\"source3\",\"unit\":\"type3:imei00000003\",\"name\":\"Source 3\", \"organization\": \"organization C\"}";
//
//        TestOutputTopic<String, JsonNode> outputTopic = testDriver.createOutputTopic("test.topic", Serdes.String().deserializer(), jsonDeserializer);
//        inputTopic.pipeInput(sensorRecord);
//
//        ObjectMapper mapper = new ObjectMapper();
//        JsonNode sensorJson = mapper.readTree(sensorRecord);
//        JsonNode sourceJson = mapper.readTree(sourceRecord);
//        ((ObjectNode)sensorJson.get("event")).setAll((ObjectNode)sourceJson);
//
//        System.out.printf("Expect: %s%n", sensorJson.toString());
//
//        assertThat(outputTopic.readValue().toString()).isEqualTo(sensorJson.toString());
//    }

//    @Ignore
//    @Test
//    public void validatePassThrough() {
//        ObjectNode json = objectMapper.createObjectNode()
//                .put("property", "value")
//                .put("property2", "value2");
//
//        System.out.printf("Sending Value: %s%n", json.toString());
//        inputTopic.pipeInput(json.toString());
//        KeyValue<String, String> outputRecord = outputTopic.readKeyValue();
//        assertThat(outputRecord.value).isEqualTo(json.toString());
//    }

    @AfterEach
    public void tearDown() {
//        testDriver.close();
    }

}
