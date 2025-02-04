/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cflt.dfederico.streams;

import com.fasterxml.jackson.databind.JsonNode;
import io.cflt.dfederico.streams.errors.CatchAllErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * This class is a Kafka Streams application that reads from a topic, enriches the data with data from another topic and writes the enriched data to another topic.
 */
@Slf4j
public class ZFEnrich {

//    //TODO MOVE THESE TO A PROPERTY FILE
//    public static String SENSORS_RAW_INPUT_TOPIC = "sensors.raw";
//    public static String SOURCES_DATA_INPUT_TOPIC = "sources.data";
//    public static String SOURCES_TABLE_TOPIC = "sources.table";
//
//    public static void main(String[] args) {
//        System.out.println("Starting ZFEnrich Stream Application");
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
//        final Topology topology = getTopology(builder, SENSORS_RAW_INPUT_TOPIC, SOURCES_DATA_INPUT_TOPIC);
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
//    public static Topology getTopology(StreamsBuilder builder, String rawSensorTopic, String sensorSourcesTopic) {
//
//        final KStream<String, JsonNode> sensorDataSource = builder.stream(rawSensorTopic, StreamUtils.stringGenericJsonConsumer)
//                .selectKey(((key, value) -> value.get("source").textValue()));
//
//        KTable<String, JsonNode> sourcesTable = builder.stream(sensorSourcesTopic, StreamUtils.stringGenericJsonConsumer)
//                .map(((key, value) -> new KeyValue<>(value.get("id").textValue(), value)))
//                //.repartition()
//                .toTable(Materialized.<String, JsonNode, KeyValueStore<Bytes, byte[]>>as("source-ktable-store")
//                        .withKeySerde(Serdes.String())
//                        .withValueSerde(StreamUtils.genericJsonSerde));
//        sourcesTable.toStream().to(SOURCES_TABLE_TOPIC, Produced.with(Serdes.String(), StreamUtils.genericJsonSerde));
//
//        sensorDataSource
//            .map(((key, value) -> new KeyValue<>(value.get("source").textValue(), value)))
//            .peek((key, node) -> System.out.printf("peek before: %s%n", node))
//            //Need to provide the Serde for the key, the stream value and the table value via the Joined configuration object.
//            //.leftJoin(sourcesTable, SENSOR_SOURCE_JOINER, Joined.with(Serdes.String(), jsonSerde, jsonSerde))
//            .join(sourcesTable, StreamUtils.SENSOR_ASSET_JOINER, Joined.with(Serdes.String(), StreamUtils.genericJsonSerde, StreamUtils.genericJsonSerde))
//            .filter((key, value) -> value.has("type"))
//            .peek((key, node) -> System.out.printf("peek after: %s%n", node))
//            .to(StreamUtils.TOPIC_EXTRACTOR_BY_SENSOR_TYPE, Produced.with(Serdes.String(), StreamUtils.genericJsonSerde));
//
//        return builder.build();
//    }
}

