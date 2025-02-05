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

import io.cflt.dfederico.streams.processors.DeduplicationProcessor;
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde;
import io.cflt.dfederico.streams.errors.CatchAllErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class NegativeBalances {

    public static String BALANCES_TOPIC_PROPERTY_KEY = "balances.topic.name";
    public static String NOTIFICATIONS_TOPIC_PROPERTY_KEY = "notifications.topic.name";
    //    public static String SOURCES_TABLE_TOPIC = "sources.table";

    public static void main(String[] args) {
        System.out.println("Starting Negative Balance Notification Stream Application");
        System.out.println("Loading Application Properties");
        try {
            Properties properties = StreamUtils.loadProperties(Paths.get(args[0]));
            ConfigureSerdes(properties);
            Run(properties);
        } catch (IOException e) {
            System.out.println("Provide a configuration property file as argument");
            System.err.printf("Exception while configuring application %s%n", e.getMessage());
            System.exit(1);
        }
    }

    private static void ConfigureSerdes(Properties properties) {
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, GenericAvroSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, GenericAvroSerde.class);
    }

    private static void Run(Properties properties) {
        log.info(">>>>>>>>>>>>> BUILDING STREAM WITH PROPERTIES: <<<<<<< {}", properties);
        StreamsBuilder builder = new StreamsBuilder();
        final Topology topology = getTopology(builder, properties);

        try (final KafkaStreams streams = new KafkaStreams(topology, properties)) {
            streams.setUncaughtExceptionHandler(new CatchAllErrorHandler());
            final CountDownLatch latch = new CountDownLatch(1);

            // attach shutdown handler to catch control-c
            Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
                @Override
                public void run() {
                    streams.close();
                    latch.countDown();
                }
            });

            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }

    public static Topology getTopology(StreamsBuilder builder, Properties properties) {
        String balancesTopic = properties.getProperty(BALANCES_TOPIC_PROPERTY_KEY);
        String notificationsTopic = properties.getProperty(NOTIFICATIONS_TOPIC_PROPERTY_KEY);

        // Add the state store for the deduplication processor
        DeduplicationProcessor.AddStateStore(builder);

        final KStream<GenericRecord, GenericRecord> balances = builder.stream(balancesTopic);
        balances.peek((key, value) -> System.out.printf("INCOMING - key: %s, value: %s%n", key, value.get("balance")))
                .process(new DeduplicationProcessor.Supplier(), DeduplicationProcessor.NEGATIVE_BALANCE_STORE)
//                .print(Printed.toSysOut());
                .peek((key, value) -> System.out.printf("NOTIFY - key: %s, value: %s%n", key, value.get("balance")))
                .to(notificationsTopic);

        return builder.build();
    }
}

