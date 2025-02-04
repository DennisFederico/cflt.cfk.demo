package io.cflt.dfederico.streams.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.cflt.dfederico.streams.StreamUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.LinkedHashMap;

@Slf4j
public class GlobalAssetsStoreCDCUpdater implements Processor<LinkedHashMap<String, ?>, LinkedHashMap<String, ?>, Void, Void> {

    private final String storeName;
    private KeyValueStore<String, JsonNode> store;

    public GlobalAssetsStoreCDCUpdater(String storeName) {
        this.storeName = storeName;
    }

    @Override
    public void init(ProcessorContext<Void, Void> context) {
        Processor.super.init(context);
        store = context.getStateStore(storeName);
    }

    @Override
    public void process(Record<LinkedHashMap<String, ?>, LinkedHashMap<String, ?>> record) {
        String assetId = record.key().get("id").toString();
        //The 'op' fiend will contain the Change tye (r=read-snapshot, c=create, u=update, d=delete)
        String op = record.value().get("op").toString();
        //If the operation is 'd', we remove the record from the store
        switch (op) {
            case "d":
                store.delete(assetId);
                break;
            case "r":
            case "c":
            case "u":
            default:
                //Records are nested inside the CDC Schema at after.document
//                ObjectNode assetObjectNode = StreamUtils.buildObjectNodeFromDynamoJson(((LinkedHashMap<String, Object>)record .value().get("after")).get("document").toString());
//                store.put(assetId, assetObjectNode);
                break;
        }
        log.info("Updated Asset Store with record: {}", store.get(assetId));
        log.info("");
    }
}
