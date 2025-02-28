# Some steps to validate resilience during Rolling Updates of Confluent Platform

This is a simple approach to have a producer and many consumers communicating over the same topic confirm resiliency during a rolling restart of the platform (i.e. upgrades).

Don't forget to give permissions to the connecting user over the topic create, for read, write and the Consumer Group.

## Create a topic with enough partitions

Ideally we want a topic with a leader partition on each Broker. I.e. 3 Broker = 3 Partitions at leat.

```shell
$ kafka-topics \
    --bootstrap-server <bootstrap:9092> \
    --command-config <connection.properties> \
    --create \
    --topic my_pizza_topic \
    --config min.insync.replicas=2 \
    --replication-factor=3 \
    --partitions 3
```

The `replication factor` and `min.insync.replicas` guarantee a high-available topic during rolling restarts.

## Kafka Connect to Produce message

This is a sample configuration of a [Datagen](https://www.confluent.io/hub/confluentinc/kafka-connect-datagen) connector.

```json
{
    "name": "pizza_orders_datagen",
    "config": {
        "name": "pizza_orders_datagen",
        "connector.class": "io.confluent.kafka.connect.datagen.DatagenConnector",
        "tasks.max": "1",
        "value.converter": "org.apache.kafka.connect.json.JsonConverter",
        "kafka.topic": "my_pizza_topic",
        "max.interval": "250",
        "iterations": "10000",
        "quickstart": "pizza_orders",
        "producer.override.acks": "all"
    }
}
```

The example produces 10K messages, a message every quater of second, it should run for 40 minutes. Choose the values according to your estimate of the upgrade duration, but give it enough throughput to make it worthwhile.

Even when at Worker level the `acks=all` should be the default, you could make sure by override at connector config.

Using standard Json converter for convenience, it can be "readable" when consuming and doesn't need Schema Registry (which I can't remember if you have it).

You can find `archetypes`  other tha `pizza_orders` [here](https://github.com/confluentinc/kafka-connect-datagen/tree/master/src/main/resources).

## (Alternative) Producing with kafka-console-perf-test

If you don't have Connect available...

```shell
$ kafka-producer-perf-test \
    --topic my_pizza_topic \
    --throughput 4 \
    --num-records 10000 \
    --record-size 5000 \
    --producer.config <connection.properties> \
    --producer-props acks=all bootstrap.servers=<bootstrap:9092>
```

## Consuming Messages

Have more than one of these, but keep the same `group.id`

```shell
$ kafka-console-consumer \
    --bootstrap-server <bootstrap:9092> \
    --consumer.config <connection.properties> \
    --group MY_FIXED_GROUP_ID \
    --from-beginning \
    --property print.value=false \
    --property print.partition=true \
    --property print.offset=true
```

**NOTE** That printing the message is been disable to avoid flooding the screen, instead it will print partition and offset per message, a more manegable widht.

You can argue why not using `kafka-consumer-perf-test` instead, like the producer perf it is not designed for resilience but measuring performance, you need to set a timeout that might interfer and give you a false impression of faiure.

**IMPORTANT** The consumers could be run in the background with nohup and &, but you then need to send a SIGTERM to the process as it won't stop and you need to direct the output to a file to capture the summary of the total messages read when it terminates.
