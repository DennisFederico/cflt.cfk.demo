package io.cflt.dfederico.streams;

import io.cflt.dfederico.streams.errors.CatchAllErrorHandler;
import io.cflt.dfederico.streams.processors.GlobalAssetsStoreCDCUpdater;
import io.cflt.dfederico.streams.processors.SetTargetTopicInHeader;
import io.cflt.dfederico.streams.processors.ZFSensorAssetJoinerProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class ZFEnrichWithGlobalStoreAndRouteByType {
//
//    //TODO MOVE THESE TO A PROPERTY FILE
//    public static String ASSETS_CDC_TOPIC = "sbx-lg-poc-kafka-cdc";
//    public static String SENSORS_RAW_INPUT_TOPIC = "sensors.raw";
//
//    public static void main(String[] args) {
//        System.out.println("Starting Negative Balances Stream Application");
//        System.out.println("Loading Application Properties");
//        try {
//            final Properties properties = StreamUtils.loadProperties(Paths.get(args[0]));
//            run(properties);
//        } catch (IOException e) {
//            System.out.println("Provide a configuration property file as argument");
//            System.err.printf("Exception while configuring application %s%n", e.getMessage());
//            System.exit(1);
//        }
//    }
//
//    private static void run(Properties properties) {
//        log.info(">>>>>>>>>>>>> BUILDING STREAM WITH PROPERTIES: <<<<<<< {}", properties);
//        final StreamsBuilder builder = new StreamsBuilder();
//        final Topology topology = getTopology(builder, ASSETS_CDC_TOPIC, SENSORS_RAW_INPUT_TOPIC);
//
//        try (final KafkaStreams streams = new KafkaStreams(topology, properties)) {
//            streams.setUncaughtExceptionHandler(new CatchAllErrorHandler());
//            final CountDownLatch latch = new CountDownLatch(1);
//
//            // attach shutdown handler to catch control-c
//            Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
//                @Override
//                public void run() {
//                    streams.close();
//                    latch.countDown();
//                }
//            });
//
//            streams.start();
//            latch.await();
//        } catch (Throwable e) {
//            System.exit(1);
//        }
//        System.exit(0);
//    }
//
//    //TODO CHANGE STRING ARGUMENTS WITH A CONFIG CLASS FOR THIS USE CASE
//    public static Topology getTopology(StreamsBuilder builder, String assetsCDCTopic, String sensorsInputTopic) {
//
//        // ... We build an internal HashMap (GlobalStore) with the assets data to enrich the sensor data
//        // Synchronized with the assets table topic
//        builder.addGlobalStore(
//                Stores.keyValueStoreBuilder(
//                        Stores.persistentKeyValueStore("global-assets-store"),
//                        Serdes.String(),
//                        StreamUtils.genericJsonSerde
//                ),
//                assetsCDCTopic,
//                StreamUtils.schemaJsonSchemaJsonConsumer,
//                () -> new GlobalAssetsStoreCDCUpdater("global-assets-store")
//        );
//
//        //THIS IS A LOCAL STORE THAT WILL BE USED TO STORE THE TOPIC NAMES
//        String topicNameStore = "topic-names-by-type-store";
//        builder.addStateStore(
//                Stores.keyValueStoreBuilder(
//                        Stores.persistentKeyValueStore(topicNameStore),
//                        Serdes.String(),
//                        Serdes.String()
//                )
//        );
//
//        // ... We enrich the sensor data with the assets data using a Processor that has access to the "HashMap" of assets
//        builder.stream(sensorsInputTopic, StreamUtils.stringGenericJsonConsumer)
//                .peek((key, node) -> System.out.printf("peek before: %s%n", node))
//                //TODO - REPLACE THE FILTER WITH A BRANCH WHOSE DEFAULT BRANCH SENDS THE RECORDS WITH NO SOURCE TO A UNKNOWNS TOPIC
//                .filter((key, value) -> value.hasNonNull("source"))
//                .process(() -> new ZFSensorAssetJoinerProcessor("global-assets-store"))
//                .peek((key, node) -> System.out.printf("peek after: %s%n", node))
//                .selectKey((key, value) -> value.get("source").textValue())
//                .process(SetTargetTopicInHeader::new, topicNameStore)
//                .to(StreamUtils.TOPIC_NAME_FROM_HEADER_EXTRACTOR, Produced.with(Serdes.String(), StreamUtils.genericJsonSerde));
//
//        return builder.build();
//    }
}
