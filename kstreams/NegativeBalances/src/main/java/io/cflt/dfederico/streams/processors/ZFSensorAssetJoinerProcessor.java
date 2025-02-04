package io.cflt.dfederico.streams.processors;

import com.fasterxml.jackson.databind.JsonNode;
import io.cflt.dfederico.streams.StreamUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ZFSensorAssetJoinerProcessor implements Processor<String, JsonNode, String, JsonNode> {

    private final String storeName;
    private ProcessorContext<String, JsonNode> context;
    private KeyValueStore<String, JsonNode> assetsStore;

    public ZFSensorAssetJoinerProcessor(String storeName) {
        this.storeName = storeName;
    }

    @Override
    public void init(ProcessorContext<String, JsonNode> context) {
        this.context = context;
        this.assetsStore =context.getStateStore(storeName);
    }


    //TODO - SHOULD WE FORWARD EVERY SINGLE RECORD OR A "LIST OF RECORDS" (from the PoV of Transactionality)?
    @Override
    public void process(Record<String, JsonNode> record) {
        String recordSource = record.value().get("source").textValue();

        JsonNode asset = assetsStore.get(recordSource);
        if (asset == null) return; //NO JOIN

        //Original Record joined with asset
        JsonNode joinedRecord = StreamUtils.SENSOR_ASSET_JOINER.apply(record.value().deepCopy(), asset);
        context.forward(record.withValue(joinedRecord));

        while (asset != null && asset.hasNonNull("parent")) {
            asset = assetsStore.get(asset.get("parent").textValue());
            joinedRecord = StreamUtils.SENSOR_ASSET_JOINER.apply(record.value().deepCopy(), asset);
            context.forward(record.withValue(joinedRecord));
        }
    }
}
