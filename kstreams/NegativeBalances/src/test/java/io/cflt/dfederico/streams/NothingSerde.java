package io.cflt.dfederico.streams;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Arrays;
import java.util.Map;

public class NothingSerde<T> implements Serializer<T>, Deserializer<T>, Serde<T> {

    @Override
    public void configure(final Map<String, ?> configuration, final boolean isKey) {}

    @Override
    public T deserialize(final String topic, final byte[] bytes) {
        if (bytes != null) {
            throw new IllegalArgumentException("Expected [" + Arrays.toString(bytes) + "] to be null.");
        } else {
            return null;
        }
    }

    @Override
    public byte[] serialize(final String topic, final T data) {
        if (data != null) {
            throw new IllegalArgumentException("Expected [" + data + "] to be null.");
        } else {
            return null;
        }
    }

    @Override
    public void close() {}

    @Override
    public Serializer<T> serializer() {
        return this;
    }

    @Override
    public Deserializer<T> deserializer() {
        return this;
    }
}
