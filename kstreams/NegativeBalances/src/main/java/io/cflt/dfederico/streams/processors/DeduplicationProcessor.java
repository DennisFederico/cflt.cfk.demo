package io.cflt.dfederico.streams.processors;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

public class DeduplicationProcessor implements Processor<GenericRecord, GenericRecord, GenericRecord, GenericRecord> {

    public static String NEGATIVE_BALANCE_STORE = "negative-balance-store";

    private ProcessorContext<GenericRecord, GenericRecord> context;
    private KeyValueStore<Integer, Boolean> stateStore;

    public static void AddStateStore(StreamsBuilder builder) {
        StoreBuilder<KeyValueStore<Integer, Boolean>> negativeBalanceStoreBuilder =
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(NEGATIVE_BALANCE_STORE), Serdes.Integer(), Serdes.Boolean()
                );
        builder.addStateStore(negativeBalanceStoreBuilder);
    }

    @Override
    public void init(ProcessorContext<GenericRecord, GenericRecord> context) {
        this.context = context;
        this.stateStore = context.getStateStore(NEGATIVE_BALANCE_STORE);
    }

    @Override
    public void process(Record<GenericRecord, GenericRecord> record) {

        Integer acctId = (Integer) record.key().get("id");
        Double currentBalance = (Double) record.value().get("balance");

        Boolean alreadyNegative = stateStore.get(acctId);

        if (currentBalance < 0 && alreadyNegative == null) {
            stateStore.put(acctId, true);
            context.forward(record);
        } else if (currentBalance >= 0 && alreadyNegative != null) {
            stateStore.delete(acctId);
        }
    }

    @Override
    public void close() {
        // Any cleanup if needed
    }

    public static class Supplier implements ProcessorSupplier<GenericRecord, GenericRecord, GenericRecord, GenericRecord> {
        @Override
        public Processor<GenericRecord, GenericRecord, GenericRecord, GenericRecord> get() {
            return new DeduplicationProcessor();
        }
    }
}