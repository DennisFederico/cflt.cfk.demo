package io.cflt.dfederico.streams.errors;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.ErrorHandlerContext;

import java.util.Map;

@Slf4j
public class DeserializationHandler implements DeserializationExceptionHandler {

//    KafkaProducer<byte[], byte[]> producer;
    String dlqTopic;
    Map<String, Object> configs;

    @Override
    public void configure(Map<String, ?> configs) {
        log.info("Configuring DeserializationHandler");
        this.configs = (Map<String, Object>) configs;
        this.configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        this.configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        this.dlqTopic = (String) this.configs.get("dlq.topic.name");
    }

    @Override
    public DeserializationHandlerResponse handle(ErrorHandlerContext context, ConsumerRecord<byte[], byte[]> record, Exception exception) {
        log.error("Handling deserialization exception for record: {}", record, exception);

        try (Producer<byte[], byte[]> producer = new KafkaProducer<>(this.configs)) {
            ProducerRecord<byte[], byte[]> producerRecord = new ProducerRecord<>(dlqTopic, null, record.value());
            producerRecord.headers().add("key", record.key());
            producerRecord.headers().add("exception", exception.getMessage().getBytes());
            producer.send(producerRecord);
        } catch (Exception e) {
            log.error("Error sending record to DLQ", e);
            throw new RuntimeException(e);
        }
        return DeserializationHandlerResponse.CONTINUE;
    }
}
