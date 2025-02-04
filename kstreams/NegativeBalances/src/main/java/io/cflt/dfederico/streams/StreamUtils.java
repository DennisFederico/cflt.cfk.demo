package io.cflt.dfederico.streams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cflt.dfederico.streams.errors.CatchAllErrorHandler;
import io.cflt.dfederico.streams.errors.DeserializationHandler;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.apache.kafka.streams.processor.TopicNameExtractor;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class StreamUtils {

    public static Properties loadProperties(Path filepath) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(filepath)) {
            properties.load(reader);
            setAdditionalProperties(properties);
            return properties;
        } catch (IOException e) {
            log.error("Error reading properties file: {}", filepath, e);
            throw e;
        }
    }

    private static void setAdditionalProperties(Properties properties) {
        properties.put(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG, CatchAllErrorHandler.class);
        properties.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, DeserializationHandler.class.getName());
    }

//    public static final Serializer<JsonNode> genericJsonSerializer = new JsonSerializer();
//    public static final Deserializer<JsonNode> genericJsonDeserializer = new JsonDeserializer();
//    public static final Serde<JsonNode> genericJsonSerde = Serdes.serdeFrom(genericJsonSerializer, genericJsonDeserializer);
//    public static final Consumed<String, JsonNode> stringGenericJsonConsumer = Consumed.with(Serdes.String(), genericJsonSerde);
//
//    public static final Serializer<LinkedHashMap<String, ?>> schemaJsonSerializer = new KafkaJsonSchemaSerializer<>();
//    public static final Deserializer<LinkedHashMap<String, ?>> schemaJsonDeserializer = new KafkaJsonSchemaDeserializer<>();
//    public static final Serde<LinkedHashMap<String, ?>> schemaJsonSerde = Serdes.serdeFrom(schemaJsonSerializer, schemaJsonDeserializer);
//    public static final Serializer<LinkedHashMap<String, ?>> keySchemaJsonSerializer = new KafkaJsonSchemaSerializer<>();
//    public static final Deserializer<LinkedHashMap<String, ?>> keySchemaJsonDeserializer = new KafkaJsonSchemaDeserializer<>();
//    public static final Serde<LinkedHashMap<String, ?>> keySchemaJsonSerde = Serdes.serdeFrom(keySchemaJsonSerializer, keySchemaJsonDeserializer);

//    public static final Consumed<LinkedHashMap<String, ?>, LinkedHashMap<String, ?>> schemaJsonSchemaJsonConsumer = Consumed.with(keySchemaJsonSerde, schemaJsonSerde);

//    static {
//        //TODO MOVE THIS INTO THE PROPERTIES FILE
//        Map<String, ?> jsonSchemaSerdeProps = Map.of(
//                KafkaJsonSchemaDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "https://psrc-yo2rpj.europe-southwest1.gcp.confluent.cloud",
//                KafkaJsonSchemaDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO",
//                KafkaJsonSchemaDeserializerConfig.USER_INFO_CONFIG, "V77IE3KPQBXM4OKB:LudXbmH5FwXHH4ZiPsRrHdlrr2sLatLtK9d8GnW8RYS3OZXbe83Wte+qMTtA5hBU",
//                KafkaJsonSchemaSerializerConfig.AUTO_REGISTER_SCHEMAS, false);
//
//        schemaJsonSerializer.configure(jsonSchemaSerdeProps, false);
//        schemaJsonDeserializer.configure(jsonSchemaSerdeProps, false);
//        keySchemaJsonSerializer.configure(jsonSchemaSerdeProps, true);
//        keySchemaJsonDeserializer.configure(jsonSchemaSerdeProps, true);
//    }

    public static final ValueJoiner<JsonNode, JsonNode, JsonNode> SENSOR_ASSET_JOINER = (sensor, asset) -> {
        if (asset != null) {
            JsonNode event = sensor.get("event");
            ((ObjectNode) event).setAll((ObjectNode) asset);
        }
        return sensor;
    };

    public static final TopicNameExtractor<String, JsonNode> TOPIC_EXTRACTOR_BY_SENSOR_TYPE =
            (key, sensor, recordContext) -> sensor.get("type").textValue() + ".type";

    public static final TopicNameExtractor<String, JsonNode> TOPIC_NAME_FROM_HEADER_EXTRACTOR =
            (key, data, recordContext) -> {
                Header topicNameHeader = recordContext.headers().lastHeader("target-topic");
                return topicNameHeader != null ? new String(topicNameHeader.value()) : "unknown.type";
            };

//    public static ObjectNode buildObjectNodeFromDynamoJson(String jsonAssetFromDynamo) {
//        EnhancedDocument assetAsDocument = EnhancedDocument.fromJson(jsonAssetFromDynamo);
//        Map<String, AttributeValue> map = assetAsDocument.toMap();
//        Map<String, Object> currentObject = new HashMap<>();
//        mapToJson(map, currentObject, null);
//        ObjectMapper obj = new ObjectMapper();
//        return obj.convertValue(currentObject, ObjectNode.class);
//    }

//    private static void mapToJson(Map<String, AttributeValue> nextNode, Map<String, Object> currentObject, String prevKey) {
//        for (Map.Entry<String, AttributeValue> entry : nextNode.entrySet()) {
//            AttributeValue.Type type = entry.getValue().type();
//            //TERMINAL TYPES
//            if (type == AttributeValue.Type.S || type == AttributeValue.Type.N || type == AttributeValue.Type.B) {
//                currentObject.put(prevKey, entry.getValue().s());
//            }
//            //NON TERMINAL TYPES
//            else if (type == AttributeValue.Type.M) {
//                HashMap<String, Object> nextNodeMap = new HashMap<>();
//                mapToJson(entry.getValue().m(), nextNodeMap, entry.getKey());
//                currentObject.putAll(nextNodeMap);
//            }
//        }
//    }
}
