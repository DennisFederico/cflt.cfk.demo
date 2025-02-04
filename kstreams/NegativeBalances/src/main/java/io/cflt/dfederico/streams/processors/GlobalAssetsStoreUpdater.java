package io.cflt.dfederico.streams.processors;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class GlobalAssetsStoreUpdater implements Processor<String, JsonNode, Void, Void> {

    private final String storeName;
    private KeyValueStore<String, JsonNode> store;

    public GlobalAssetsStoreUpdater(String storeName) {
        this.storeName = storeName;
    }

    @Override
    public void init(ProcessorContext<Void, Void> context) {
        Processor.super.init(context);
        store = context.getStateStore(storeName);
    }

    @Override
    public void process(Record<String, JsonNode> record) {
        if (record.value() == null) {
            store.delete(record.key());
        } else {
            store.put(record.key(), record.value());
        }
    }
}
