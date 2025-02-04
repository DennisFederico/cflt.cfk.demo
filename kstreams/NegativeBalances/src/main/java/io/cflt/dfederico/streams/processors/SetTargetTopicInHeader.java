package io.cflt.dfederico.streams.processors;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.clients.admin.Admin;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Slf4j
public class SetTargetTopicInHeader implements Processor<String, JsonNode, String, JsonNode> {

    Cancellable scheduledUpdate;
    KeyValueStore<String, String> topicNameStore;
    Admin kafkaAdminClient;
    //TODO - USE A PROCESSOR SUPPLIER TO PARAMETERIZE THE STORE NAME AND TOPIC NAMING CONVENTION
    final String topicNameStoreName = "topic-names-by-type-store";
    //final Duration scheduleInterval = Duration.ofMinutes(5);
    final Duration scheduleInterval = Duration.ofSeconds(30);
    final String topicNameSuffix = ".type";
    ProcessorContext<String, JsonNode> context;

    @Override
    public void init(ProcessorContext<String, JsonNode> context) {
        Processor.super.init(context);
        this.context = context; //Needed to access headers
        topicNameStore = context.getStateStore(topicNameStoreName);
        kafkaAdminClient = Admin.create(context.appConfigs());
        updateTopicNameStore(context.currentSystemTimeMs());
        scheduledUpdate = context.schedule(scheduleInterval, PunctuationType.WALL_CLOCK_TIME, this::updateTopicNameStore);
    }

    @Override
    public void process(Record<String, JsonNode> record) {
        String type = record.value().hasNonNull("type") ? record.value().get("type").textValue() : ""; //We can use unknown as default type, it will match with the actual 'unknown.type' topic
        String topicName = topicNameStore.get(type + topicNameSuffix);
        //NOTE - If there's no topic to send the record to we just skip adding the header, the topicNameExtractor handles the routing to 'unknown.type'
        if (topicName != null) {
            record.headers().add("target-topic", topicName.getBytes());
        } else {
            log.warn("No target topic found for type: {}", type);
        }
        context.forward(record);
    }

    @Override
    public void close() {
        kafkaAdminClient.close(Duration.ofSeconds(60)); //Could we get a timeout?
        scheduledUpdate.cancel();
        Processor.super.close();
    }

    private void updateTopicNameStore(long timestamp) {
        //HERE WE UPDATE THE STORE WITH THE LATEST TOPIC NAMES
        //NOTE THAT USER PERMISSIONS ALSO CONSTRAINT THE TOPICS LISTED
        try {
            Set<String> topicNames = kafkaAdminClient.listTopics().names().get();
            int topicCount = topicNames.size();

            //Remove topics from the store that are not in the retrieved set
            //Iterators are usually not ConcurrentModification safe
            //We build a collection of the values to check what needs to be removed
            //in a following step
            Set<String> topicsToDelete = new HashSet<>();
            try (KeyValueIterator<String, String> all = topicNameStore.all()) {
                all.forEachRemaining(entry -> {
                    if (topicNames.contains(entry.value)) {
                        topicNames.remove(entry.value); //Already exists
                    } else {
                        topicsToDelete.add(entry.key); //Must be deleted
                    }
                });
            }

            int topicsAdded = topicNames.size();
            int topicsDeleted = topicsToDelete.size();

            //Delete the entries in the store
            topicsToDelete.forEach(topicNameStore::delete);

            //Add remaining topics
            topicNames.forEach(topicName -> {
                topicNameStore.putIfAbsent(topicName, topicName);
            });

            log.info("Updated topic names store: {} entries processed, {} topics added, {} topics deleted", topicCount, topicsAdded, topicsDeleted);

        } catch (InterruptedException e) {
            //Here we might be interrupted by a shutdown
            log.warn("Cannot refresh topic list", e.getCause());
        } catch (ExecutionException e) {
            //Here the actual task of fetching the topic list failed
            throw new RuntimeException("Cannot refresh topic list", e.getCause());
        }
    }
}

