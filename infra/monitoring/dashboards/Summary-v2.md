# Pending changes (Missing Metrics)

## Dashboard: confluent-platform-kraft.json (Confluent Platform Overview)

All metrics display but filtering is not available

### Section: Variables and Labels

- Variable: kafka_connect_cluster_id
- Problem: Not Available
- Impact: Should affect filtering of Panels in Kafka Connect section, but the non-value works like 'ALL'
- Solution: ***Pending Investigation***

## Dashboard: kraft.json (KRaft)

No problem found after adding metrics rules to the kraftcontroller CRD and using the dashboard transform script

## Dashboard: kafka-cluster-kraft.json (Kafka Cluster)

Only q metrics missing.

### Section: Thread utilization

- Panel: Request Handler Avg Percent
- Metric: `kafka_server_kafkarequesthandlerpool_requesthandleravgidlepercent_total`
- Problem: Metric Renamed
- Resolution: change to `kafka_server_kafkarequesthandlerpool_controlplanerequesthandleravgidlepercent`

### Section: Tiered Storega

- Not in scope

## Dashboard: schema-registry-cluster.json (Schema Registry Cluster)

Only 3 Metrics/Panel not displaying data

### Section: Connections

- Panel: Active Connections
- Metrics: `kafka_schema_registry_jetty_metrics_connections_active`
- Problem: Metric not available
- Resolution: ***Pending Investigation***

---

- Panel: Requets Rate
- Metrics: `kafka_schema_registry_jersey_metrics_request_rate`
- Problem: Metric not available
- Resolution: ***Pending Investigation***

---

- Panel: Requests latency 99p
- Metrics: `kafka_schema_registry_jersey_metrics_request_latency_99`
- Problem: Metric not available
- Resolution: ***Pending Investigation**

## Dashboard: kafka-connect-cluster.json (Kafka Connect Cluster)

No problems remaining after adding the metrics rules and applying the transformation script v.2

## Dashboard: ksqldb-cluster.json (ksqlDB Cluster)

No problems found, the ones for the state store take longer to show on the playground, maybe due to scrapping time (by experience could be the agent exporter version).

## Dashboard: kafka-topics.json (Kafka Topics)

No problems found.

## Dashboard: cluster-linking.json (Cluster Linking)

***Pending Review***, need to create some links to put data in

## Dashboard: kafka-consumer.json (Kafka Consumer) / kafka-producer.json (Kafka Producer)

Not Data present (need to Invesigate labels and filters first)

## Dashboard: kafka-quotas.json (Kafka Quotas)

This panel only populates once a the first quota is created... ***NEXT TO INVESTIGATE*** after Cluster Link.
