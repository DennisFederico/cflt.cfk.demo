# JMX Exporter configuration for CFK CRDs

The Stock images used by CFK come with empty (template) for `jmx-exporter.yaml`. Whitelist, Blacklist and Rules need to be added to the CRDs.

Add these to each CRD under `specs`

## Kraft Controller & Kafka Brokers

These services have the same base binary (MBeans) and can share the same configuration

```yaml
specs:
  metrics:
    prometheus:
      blacklist:
        - "kafka.consumer:type=*,id=*"
        - "kafka.consumer:type=*,client-id=*"
        - "kafka.consumer:type=*,client-id=*,node-id=*"
        - "kafka.producer:type=*,id=*"
        - "kafka.producer:type=*,client-id=*"
        - "kafka.producer:type=*,client-id=*,node-id=*"
        - "kafka.*:type=kafka-metrics-count,*"
        - "kafka.admin.client:*"
        - "kafka.server:type=*,cipher=*,protocol=*,listener=*,networkProcessor=*"
        - "kafka.server:type=app-info,id=*"
        - "kafka.rest:*"
        - "rest.utils:*"
        - "io.confluent.common.security.jetty:*"
        - "io.confluent.rest:*"
        - "confluent.metadata.service:type=app-info,id=*"
        - "confluent.metadata.service:type=app-info,client-id=*"
        - "confluent.metadata:type=kafkaauthstore,*"
      rules:
        # This rule is more specific than the next rule; it has to come before it otherwise it will never be hit
        # "kafka.server:type=*,name=*, client-id=*, topic=*, partition=*"
        - pattern: kafka.server<type=(.+), name=(.+), clientId=(.+), topic=(.+), partition=(.*)><>Value
          name: kafka_server_$1_$2
          type: GAUGE
          cache: true
          labels:
            clientId: "$3"
            topic: "$4"
            partition: "$5"
        # This is by far the biggest contributor to the number of sheer metrics being produced.
        # Always keep it near the top for the case of probability when so many metrics will hit the first condition and exit.
        # "kafka.cluster:type=*, name=*, topic=*, partition=*"
        # "kafka.log:type=*,name=*, topic=*, partition=*"
        - pattern: kafka.(\w+)<type=(.+), name=(.+), topic=(.+), partition=(.+)><>Value
          name: kafka_$1_$2_$3
          type: GAUGE
          cache: true
          labels:
            topic: "$4"
            partition: "$5"
        # Next two rules are similar; Value version is a GAUGE; Count version is not
        - pattern: kafka.server<type=(.+), name=(.+), clientId=(.+), brokerHost=(.+), brokerPort=(.+)><>Value
          name: kafka_server_$1_$2
          type: GAUGE
          cache: true
          labels:
            clientId: "$3"
            broker: "$4:$5"
        - pattern: kafka.server<type=(.+), name=(.+), clientId=(.+), brokerHost=(.+), brokerPort=(.+)><>Count
          name: kafka_server_$1_$2
          cache: true
          labels:
            clientId: "$3"
            broker: "$4:$5"
        # Needed for Cluster Linking metrics
        # "kafka.server:type=*, name=*, *=*, *=*, *=*, *=*"
        - pattern: kafka.(\w+)<type=(.+), name=(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+)><>(Count|Value)
          name: kafka_$1_$2_$3
          cache: true
          labels:
            "$4": "$5"
            "$6": "$7"
            "$8": "$9"
            "$10": "$11"
        # "kafka.server:type=*, name=*, *=*, *=*, *=*"
        - pattern: kafka.(\w+)<type=(.+), name=(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+)><>(Count|Value)
          name: kafka_$1_$2_$3
          cache: true
          labels:
            "$4": "$5"
            "$6": "$7"
            "$8": "$9"
        # "kafka.network:type=*, name=*, request=*, error=*"
        # "kafka.network:type=*, name=*, request=*, version=*"
        - pattern: kafka.(\w+)<type=(.+), name=(.+), (.+)=(.+), (.+)=(.+)><>(Count|Value)
          name: kafka_$1_$2_$3
          cache: true
          labels:
            "$4": "$5"
            "$6": "$7"
        - pattern: kafka.(\w+)<type=(.+), name=(.+), (.+)=(.*), (.+)=(.+)><>(\d+)thPercentile
          name: kafka_$1_$2_$3
          type: GAUGE
          cache: true
          labels:
            "$4": "$5"
            "$6": "$7"
            quantile: "0.$8"
        # "kafka.rest:type=*, topic=*, partition=*, client-id=*"
        # "kafka.rest:type=*, cipher=*, protocol=*, client-id=*"
        - pattern: kafka.(\w+)<type=(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+)><>Value
          name: kafka_$1_$2
          cache: true
          labels:
            "$3": "$4"
            "$5": "$6"
            "$7": "$8"
        # Count and Value
        # "kafka.server:type=*, name=*, topic=*"
        # "kafka.server:type=*, name=*, clientId=*"
        # "kafka.server:type=*, name=*, delayedOperation=*"
        # "kafka.server:type=*, name=*, fetcherType=*"
        # "kafka.network:type=*, name=*, networkProcessor=*"
        # "kafka.network:type=*, name=*, processor=*"
        # "kafka.network:type=*, name=*, request=*"
        # "kafka.network:type=*, name=*, listener=*"
        # "kafka.log:type=*, name=*, logDirectory=*"
        # "kafka.log:type=*, name=*, op=*"
        # "kafka.rest:type=*, node-id=*, client-id=*"
        - pattern: kafka.(\w+)<type=(.+), name=(.+), (.+)=(.+)><>(Count|Value)
          name: kafka_$1_$2_$3
          cache: true
          labels:
            "$4": "$5"
        # "kafka.consumer:type=*, topic=*, client-id=*"
        # "kafka.producer:type=*, topic=*, client-id=*"
        # "kafka.rest:type=*, topic=*, client-id=*"
        # "kafka.server:type=*, broker-id=*, fetcher-id=*"
        # "kafka.server:type=*, listener=*, networkProcessor=*"
        - pattern: kafka.(\w+)<type=(.+), (.+)=(.+), (.+)=(.+)><>(Count|Value)
          name: kafka_$1_$2
          cache: true
          labels:
            "$3": "$4"
            "$5": "$6"
        # - pattern: "kafka.(.+)<type=(.+), (.+)=(.+), (.+)=(.+)><>(.+):"
        #   name: kafka_$1_$2
        #   cache: true
        #   labels:
        #     "$3": "$4"
        #     "$5": "$6"
        #     attribute_name: "$7"
        # "kafka.network:type=*, name=*"
        # "kafka.server:type=*, name=*"
        # "kafka.controller:type=*, name=*"
        # "kafka.databalancer:type=*, name=*"
        # "kafka.log:type=*, name=*"
        # "kafka.utils:type=*, name=*"
        - pattern: kafka.(\w+)<type=(.+), name=(.+)><>(Count|Value)
          name: kafka_$1_$2_$3
        # "kafka.producer:type=*, client-id=*"
        # "kafka.producer:type=*, id=*"
        # "kafka.rest:type=*, client-id=*"
        # "kafka.rest:type=*, http-status-code=*"
        # "kafka.server:type=*, BrokerId=*"
        # "kafka.server:type=*, listener=*"
        # "kafka.server:type=*, id=*"
        - pattern: kafka.(\w+)<type=(.+), (.+)=(.+)><>Value
          name: kafka_$1_$2
          cache: true
          labels:
            "$3": "$4"
        # - pattern: "kafka.(.+)<type=(.+), (.+)=(.+)><>(.+):"
        #   name: kafka_$1_$2
        #   cache: true
        #   labels:
        #     "$3": "$4"
        #     attribute_name: "$5"
        - pattern: kafka.server<type=KafkaRequestHandlerPool, name=RequestHandlerAvgIdlePercent><>OneMinuteRate
          name: kafka_server_kafkarequesthandlerpool_controlplanerequesthandleravgidlepercent
          type: GAUGE
        # "kafka.server:type=*, listener=*, networkProcessor=*, clientSoftwareName=*, clientSoftwareVersion=*"
        - pattern: kafka.server<type=socket-server-metrics, clientSoftwareName=(.+), clientSoftwareVersion=(.+), listener=(.+), networkProcessor=(.+)><>connections
          name: kafka_server_socketservermetrics_connections
          type: GAUGE
          cache: true
          labels:
            client_software_name: "$1"
            client_software_version: "$2"
            listener: "$3"
            network_processor: "$4"
        - pattern: "kafka.server<type=socket-server-metrics, listener=(.+), networkProcessor=(.+)><>(.+):"
          name: kafka_server_socketservermetrics_$3
          type: GAUGE
          cache: true
          labels:
            listener: "$1"
            network_processor: "$2"
        # - pattern: "kafka.server<type=socket-server-metrics, listener=(.+)><>(.+):"
        #   name: kafka_server_socketservermetrics
        #   type: GAUGE
        #   cache: true
        #   labels:
        #     listener: "$1"
        #     attribute_name: "$2"
        # "kafka.coordinator.group:type=*, name=*"
        # "kafka.coordinator.transaction:type=*, name=*"
        - pattern: kafka.coordinator.(\w+)<type=(.+), name=(.+)><>(Count|Value)
          name: kafka_coordinator_$1_$2_$3
        # Percentile
        - pattern: kafka.(\w+)<type=(.+), name=(.+), (.+)=(.*)><>(\d+)thPercentile
          name: kafka_$1_$2_$3
          type: GAUGE
          cache: true
          labels:
            "$4": "$5"
            quantile: "0.$6"
        - pattern: kafka.(\w+)<type=(.+), name=(.+)><>(\d+)thPercentile
          name: kafka_$1_$2_$3
          type: GAUGE
          cache: true
          labels:
            quantile: "0.$4"
        # Additional Rules for Confluent Server Metrics
        # 'kafka.server:type=confluent-auth-store-metrics'
        - pattern: kafka.server<type=confluent-auth-store-metrics><>(.+):(.*)
          name: confluent_auth_store_metrics_$1
          type: GAUGE
          cache: true
        # 'confluent.metadata:type=*, name=*, topic=*, partition=*'
        - pattern: confluent.(\w+)<type=(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+)><>(Value|Count)
          name: confluent_$1_$2
          type: GAUGE
          cache: true
          labels:
            "$3": "$4"
            "$5": "$6"
            "$7": "$8"
        # 'confluent.metadata.service:type=*, node-id=*, client-id=*'
        - pattern: confluent.(.+)<type=(.+), (.+)=(.+), (.+)=(.+)><>Value
          name: confluent_$1_$2
          type: GAUGE
          cache: true
          labels:
            "$3": "$4"
            "$5": "$6"
        # 'confluent.metadata.service:type=*, node-id=*, client-id=*'
        - pattern: "confluent.metadata.service<type=(.+), (.+)=(.+), (.+)=(.+)><>(.+):"
          name: $1
          type: GAUGE
          cache: true
          labels:
            "$2": "$3"
            "$4": "$5"
            attribute_name: "$6"
        # 'confluent.metadata.service:type=*, client-id=*'
        # 'confluent.metadata.service:type=*, id=*'
        # 'confluent.metadata:type=*, name=*'
        # 'confluent.license:type=*, name=*'
        - pattern: confluent.(.+)<type=(.+), (.+)=(.+)><>Value
          name: confluent_$1_$2
          type: GAUGE
          cache: true
          labels:
            "$3": "$4"
        - pattern: "confluent.(.+)<type=(.+), (.+)=(.+)><>(.+):"
          name: confluent_$1_$2
          type: GAUGE
          cache: true
          labels:
            "$3": "$4"
            attribute_name: "$5"
        # Quotas
        - pattern: "kafka.server<type=(Produce|Fetch|Request), user=(.+), client-id=(.+)><>(.+):"
          name: kafka_server_$1_$4
          type: GAUGE
          cache: true
          labels:
            user: "$2"
            client-id: "$3"

        - pattern: "kafka.server<type=(Produce|Fetch|Request), user=(.+)><>(.+):"
          name: kafka_server_$1_$3
          type: GAUGE
          cache: true
          labels:
            user: "$2"

        - pattern: "kafka.server<type=(Produce|Fetch|Request), client-id=(.+)><>(.+):"
          name: kafka_server_$1_$3
          type: GAUGE
          cache: true
          labels:
            client-id: "$2"

        # Broker  Metrics
        - pattern: "kafka.server<type=BrokerTopicMetrics, name=(MessagesInPerSec|BytesInPerSec|BytesOutPerSec|TotalProduceRequestsPerSec|TotalFetchRequestsPerSec), topic=(.+)><>(Count|OneMinuteRate|FiveMinuteRate|FifteenMinuteRate)"
          name: kafka_server_brokertopicmetrics_$1_$3
          type: GAUGE
          cache: true
          labels:
            topic: "$2"

        - pattern: "kafka.server<type=BrokerTopicMetrics, name=(MessagesInPerSec|BytesInPerSec|BytesOutPerSec)><>(Count|OneMinuteRate|FiveMinuteRate|FifteenMinuteRate)"
          name: kafka_server_brokertopicmetrics_$1_$2_alltopics
          type: GAUGE

        # Network Request  Metrics
        - pattern: "kafka.network<type=RequestMetrics, name=RequestsPerSec, request=(.+), version=([0-9]+)><>(Count|OneMinuteRate|FiveMinuteRate|FifteenMinuteRate)"
          name: kafka_network_requestmetrics_requestspersec_$3
          type: GAUGE
          cache: true
          labels:
            request: "$1"

        # "kafka.server:type=raft-metrics"
        - pattern: kafka.server<type=raft-metrics><>(.+):(.*)
          name: kafka_server_raft_metrics_$1
          type: GAUGE
          cache: true

        # kafka.server:type=transaction-coordinator-metrics
        - pattern: kafka.server<type=transaction-coordinator-metrics><>(.+):(.*)
          name: kafka_server_transaction_coordinator_metrics_$1
          type: GAUGE
          cache: true

        # Needed for Cluster Linking metrics
        # kafka.server.link:type=ClusterLinkFetcherManager,name=*,clientId=&,link-name=*
        - pattern: kafka.server.link<type=ClusterLinkFetcherManager, name=(.+), (.+)=(.+), (.+)=(.+)><>Value
          name: kafka_server_link_clusterlinkfetchermanager_$1
          type: GAUGE
          cache: true
          labels:
            "$2": "$3"
            "$4": "$5"
        # kafka.server:type=cluster-link-fetcher-metrics,link-name=*,broker-id=*,fetcher-id=*, mechanism=*
        - pattern: "kafka.server<type=cluster-link-fetcher-metrics, (.+)=(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+)><>(.+):"
          name: kafka_server_cluster_link_fetcher_metrics_$9
          type: GAUGE
          labels:
            "$1": "$2"
            "$3": "$4"
            "$5": "$6"
            "$7": "$8"
        # kafka.server:type=cluster-link-fetcher-metrics,link-name=*,broker-id=*,fetcher-id=*
        - pattern: "kafka.server<type=cluster-link-fetcher-metrics, (.+)=(.+), (.+)=(.+), (.+)=(.+)><>(.+):"
          name: kafka_server_cluster_link_fetcher_metrics_$7
          type: GAUGE
          cache: true
          labels:
            "$1": "$2"
            "$3": "$4"
            "$5": "$6"
        # kafka.server:type=cluster-link-metrics, mode=*, state=*, link-name=*, name=*
        - pattern: "kafka.server<type=cluster-link-metrics, (.+)=(.+), (.+)=(.+), (.+)=(.+)><>(.+):"
          name: kafka_server_cluster_link_metrics_$7
          type: GAUGE
          labels:
            "$1": "$2"
            "$3": "$4"
            "$5": "$6"
        # kafka.server:type=cluster-link-metrics,state=*,link-name=*,name=*
        - pattern: "kafka.server<type=cluster-link-metrics, (.+)=(.+), (.+)=(.+)><>(.+):"
          name: kafka_server_cluster_link_metrics_$5
          type: GAUGE
          cache: true
          labels:
            "$1": "$2"
            "$3": "$4"
        # kafka.server:type=cluster-link-metrics,name=*,link-name=*
        - pattern: "kafka.server<type=cluster-link-metrics, (.+)=(.+)><>(.+):"
          name: kafka_server_cluster_link_metrics_$3
          type: GAUGE
          labels:
            "$1": "$2"
        # kafka.server:type=cluster-link-source-metrics,request=*,link-id=*
        - pattern: "kafka.server<type=cluster-link-source-metrics, request=(.+), link-id=(.+)><>(.+):(.+)"
          name: kafka_server_cluster_link_source_metrics_$1_$3
          value: $4
          type: GAUGE
          labels:
            request: "$1"
            link-id: "$2"
        ## Consumer Lag Offsets
        # "kafka.server:type=tenant-metrics,name=*,consumer-group=*,client-id=*,topic=*,partition=*"
        - pattern: 'kafka.server<type=tenant-metrics, member=(.+), topic=(.+), consumer-group=(.+), partition=(.+), client-id=(.+)><>(.+):(.+)'
          name: kafka_server_tenant_metrics_$6
          type: GAUGE
          labels:
            consumerGroup: "$3"
            client-id: "$5"
            topic: "$2"
            partition: "$4"

        ## Confluent Audit
        - pattern: 'kafka.server<type=confluent-audit-metrics><>(.+):(.*)'
          name: confluent_audit_log_$1
          type: GAUGE
```

## Schema Registry

```yaml
specs:
  metrics:
    prometheus:
      blacklist:
        # This will ignore the admin client metrics from SR server and will blacklist certain metrics
        # that do not make sense for ingestion.
        - kafka.producer:type=app-info,client-id=*
        - kafka.consumer:type=app-info,client-id=*
        - "kafka.admin.client:*"
        - "kafka.consumer:type=*,id=*"
        - "kafka.producer:type=*,id=*"
        # - "kafka.producer:client-id=confluent.monitoring*,*"
        # - "kafka.producer:client-id=confluent-license*,*"
        - "kafka.*:type=kafka-metrics-count,*"
      whitelist:
        - kafka.schema.registry:type=jetty-metrics
        - kafka.schema.registry:type=jersey-metrics
        - kafka.schema.registry:type=app-info,id=*
        - kafka.schema.registry:type=registered-count
        - kafka.schema.registry:type=json-schema*
        - kafka.schema.registry:type=protobuf-schemas*
        - kafka.schema.registry:type=avro-schemas*
        - kafka.schema.registry:type=kafka.schema.registry-metrics,client-id=*
        - kafka.schema.registry:type=kafka.schema.registry-coordinator-metrics,client-id=*
        - kafka.schema.registry:type=master-slave-role
        # The two lines below are used to pull the Kafka Client Producer & consumer metrics from SR Client.
        # If you care about Producer/Consumer metrics for SR, please uncomment 2 lines below.
        # Please note that this increases the scrape duration to about 1 second as it needs to parse a lot of data.
        - "kafka.consumer:*"
        - "kafka.producer:*"
      rules:
        # "kafka.schema.registry:type=jetty-metrics"
        - pattern: "kafka.schema.registry<type=jetty-metrics>([^:]+):"
          name: "kafka_schema_registry_jetty_metrics_$1"
        # "kafka.schema.registry:type=jersey-metrics"
        - pattern: "kafka.schema.registry<type=jersey-metrics>([^:]+):"
          name: "kafka_schema_registry_jersey_metrics_$1"
        # "kafka.schema.registry:type=app-info,id=*"
        - pattern: "kafka.schema.registry<type=app-info, id=(.+)><>(.+): (.+)"
          name: "kafka_schema_registry_app_info"
          value: "1"
          labels:
            client-id: "$1"
            $2: "$3"
          type: UNTYPED
        # "kafka.schema.registry:type=registered-count"
        - pattern: "kafka.schema.registry<type=registered-count>([^:]+):"
          name: "kafka_schema_registry_registered_count"
        # "kafka.schema.registry:type=json-schemas-created"
        # "kafka.schema.registry:type=json-schemas-deleted"
        # "kafka.schema.registry:type=protobuf-schemas-created"
        # "kafka.schema.registry:type=protobuf-schemas-deleted"
        # "kafka.schema.registry:type=avro-schemas-created"
        # "kafka.schema.registry:type=avro-schemas-deleted"
        - pattern: "kafka.schema.registry<type=(\\w+)-schemas-(\\w+)>([^:]+):"
          name: "kafka_schema_registry_schemas_$2"
          cache: true
          labels:
            schema_type: $1
        # kafka.schema.registry:type=master-slave-role
        - pattern: "kafka.schema.registry<type=master-slave-role><>master-slave-role"
          name: "kafka_schema_registry_master_slave_role"
          type: GAUGE
          help: "Current role of this Schema Registry instance: 1 indicates primary, 0 indicates secondary"
          cache: true
        # kafka.schema.registry:type=kafka.schema.registry-metrics,client-id=*
        # kafka.schema.registry:type=kafka.schema.registry-coordinator-metrics,client-id=*
        - pattern: "kafka.schema.registry<type=(.+), client-id=(.+)><>([^:]+):"
          name: "kafka_schema_registry_$1_$3"
          cache: true
          labels:
            client_id: $2
        # "kafka.consumer:type=app-info,client-id=*"
        # "kafka.producer:type=app-info,client-id=*"
        - pattern: "kafka.(.+)<type=app-info, client-id=(.+)><>(.+): (.+)"
          value: "1"
          name: kafka_$1_app_info
          cache: true
          labels:
            client_type: $1
            client_id: $2
            $3: $4
          type: UNTYPED
        # "kafka.consumer:type=consumer-metrics,client-id=*, protocol=*, cipher=*"
        # "kafka.consumer:type=type=consumer-fetch-manager-metrics,client-id=*, topic=*, partition=*"
        # "kafka.producer:type=producer-metrics,client-id=*, protocol=*, cipher=*"
        - pattern: "kafka.(.+)<type=(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+)><>(.+):"
          name: kafka_$1_$2_$9
          type: GAUGE
          cache: true
          labels:
            client_type: $1
            $3: "$4"
            $5: "$6"
            $7: "$8"
        # "kafka.consumer:type=consumer-node-metrics,client-id=*, node-id=*"
        # "kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*, topic=*"
        # "kafka.producer:type=producer-node-metrics,client-id=*, node-id=*"
        # "kafka.producer:type=producer-topic-metrics,client-id=*, topic=*"
        - pattern: "kafka.(.+)<type=(.+), (.+)=(.+), (.+)=(.+)><>(.+):"
          name: kafka_$1_$2_$7
          type: GAUGE
          cache: true
          labels:
            client_type: $1
            $3: "$4"
            $5: "$6"
        # "kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*"
        # "kafka.consumer:type=consumer-metrics,client-id=*"
        # "kafka.producer:type=producer-metrics,client-id=*"
        - pattern: "kafka.(.+)<type=(.+), (.+)=(.+)><>(.+):"
          name: kafka_$1_$2_$5
          type: GAUGE
          cache: true
          labels:
            client_type: $1
            $3: "$4"
        - pattern: "kafka.(.+)<type=(.+)><>(.+):"
          name: kafka_$1_$2_$3
          cache: true
          labels:
            client_type: $1
```

## Kafka Connect

```yaml
specs:
  metrics:
    prometheus:
      whitelist:
        # Engine Application Versioning Info
        - kafka.connect:type=app-info,client-id=*
        # Connect Worker Rebalancing info
        - kafka.connect:type=connect-worker-rebalance-metrics
        # Connect Co-ordinator Info
        - kafka.connect:type=connect-coordinator-metrics,*
        - kafka.connect:type=connect-metrics,*
        # Worker level metrics for the aggregate as well as per connector level
        - kafka.connect:type=connect-worker-metrics
        - kafka.connect:type=connect-worker-metrics,*
        # Engine Connector Versioning Info
        - kafka.connect:type=connector-metrics,*
        # Task level metrics for every connector running in the current node.
        - kafka.connect:type=*-task-metrics,*
        - kafka.connect:type=task-error-metrics,*
        #  Confluent Replicator JMX Stats
        - confluent.replicator:type=confluent-replicator-task-metrics,*
        # The two lines below are used to pull the Kafka Client Producer & consumer metrics from Connect Workers.
        # If you care about Producer/Consumer metrics for Connect, please uncomment 2 lines below.
        # Please note that this increases the scrape duration by about 1-2 seconds as it needs to parse a lot of data.
        - "kafka.consumer:*"
        - "kafka.producer:*"
        - "kafka.secret.registry:*"
        - "kafka.connect.oracle.cdc:*"
        - "com.mongodb.kafka.connect:*"
        - "debezium.mysql:*"
        - "debezium.sql_server:*"
        - "debezium.mongodb:*"
        - "debezium.postgres:*"
      blacklist:
        # This will ignore the admin client metrics from KSQL server and will blacklist certain metrics
        # that do not make sense for ingestion.
        - "kafka.admin.client:*"
        - "kafka.consumer:type=*,id=*"
        - "kafka.producer:type=*,id=*"
        - "kafka.producer:client-id=confluent.monitoring*,*"
        - "kafka.*:type=kafka-metrics-count,*"
        # - "kafka.*:type=app-info,*"
      rules:
      # "kafka.schema.registry:type=app-info,id=*"
        - pattern: "kafka.connect<type=app-info, client-id=(.+)><>(.+): (.+)"
          name: "kafka_connect_app_info"
          value: "1"
          labels:
            client-id: "$1"
            $2: "$3"
          type: UNTYPED
        # kafka.connect:type=connect-worker-rebalance-metrics
        - pattern: "kafka.connect<type=connect-worker-rebalance-metrics><>([^:]+)"
          name: "kafka_connect_connect_worker_rebalance_metrics_$1"
        # kafka.connect:type=connect-coordinator-metrics,client-id=*
        # kafka.connect:type=connect-metrics,client-id=*
        - pattern: "kafka.connect<type=(.+), client-id=(.+)><>([^:]+)"
          name: kafka_connect_$1_$3
          labels:
            client_id: $2
        # kafka.connect:type=connect-worker-metrics
        - pattern: "kafka.connect<type=connect-worker-metrics><>([^:]+)"
          name: kafka_connect_connect_worker_metrics_$1
          labels:
            connector: "aggregate"
        # kafka.connect:type=connect-worker-metrics,connector=*
        - pattern: "kafka.connect<type=connect-worker-metrics, connector=(.+)><>([^:]+)"
          name: kafka_connect_connect_worker_metrics_$2
          labels:
            connector: $1
        # kafka.connect:type=connector-metrics,connector=*
        - pattern: "kafka.connect<type=connector-metrics, connector=(.+)><>(.+): (.+)"
          value: "1"
          name: kafka_connect_connector_metrics
          labels:
            connector: $1
            $2: $3
          type: UNTYPED

        # https://github.com/debezium/debezium-examples/blob/main/monitoring/debezium-jmx-exporter/config.yml
        - pattern : "kafka.connect<type=connect-worker-metrics>([^:]+):"
          name: "kafka_connect_worker_metrics_$1"
        - pattern : "kafka.connect<type=connect-metrics, client-id=([^:]+)><>([^:]+)"
          name: "kafka_connect_metrics_$2"
          labels:
            client: "$1"
        - pattern: "debezium.([^:]+)<type=connector-metrics, context=([^,]+), server=([^,]+), key=([^>]+)><>RowsScanned"
          name: "debezium_metrics_RowsScanned"
          labels:
            plugin: "$1"
            name: "$3"
            context: "$2"
            table: "$4"
        - pattern: "debezium.([^:]+)<type=connector-metrics, server=([^,]+), task=([^,]+), context=([^,]+), database=([^>]+)>([^:]+)"
          name: "debezium_metrics_$6"
          labels:
            plugin: "$1"
            name: "$2"
            task: "$3"
            context: "$4"
            database: "$5"
        - pattern: "debezium.([^:]+)<type=connector-metrics, server=([^,]+), task=([^,]+), context=([^>]+)>([^:]+)"
          name: "debezium_metrics_$5"
          labels:
            plugin: "$1"
            name: "$2"
            task: "$3"
            context: "$4"
        - pattern: "debezium.([^:]+)<type=connector-metrics, context=([^,]+), server=([^>]+)>([^:]+)"
          name: "debezium_metrics_$4"
          labels:
            plugin: "$1"
            name: "$3"
            context: "$2"
```

### Additional metrics RULES for some connectors

```yaml
# https://github.com/debezium/debezium-examples/blob/main/monitoring/debezium-jmx-exporter/config.yml
  - pattern : "kafka.connect<type=connect-worker-metrics>([^:]+):"
    name: "kafka_connect_worker_metrics_$1"
  - pattern : "kafka.connect<type=connect-metrics, client-id=([^:]+)><>([^:]+)"
    name: "kafka_connect_metrics_$2"
    labels:
      client: "$1"
  - pattern: "debezium.([^:]+)<type=connector-metrics, context=([^,]+), server=([^,]+), key=([^>]+)><>RowsScanned"
    name: "debezium_metrics_RowsScanned"
    labels:
      plugin: "$1"
      name: "$3"
      context: "$2"
      table: "$4"
  - pattern: "debezium.([^:]+)<type=connector-metrics, server=([^,]+), task=([^,]+), context=([^,]+), database=([^>]+)>([^:]+)"
    name: "debezium_metrics_$6"
    labels:
      plugin: "$1"
      name: "$2"
      task: "$3"
      context: "$4"
      database: "$5"
  - pattern: "debezium.([^:]+)<type=connector-metrics, server=([^,]+), task=([^,]+), context=([^>]+)>([^:]+)"
    name: "debezium_metrics_$5"
    labels:
      plugin: "$1"
      name: "$2"
      task: "$3"
      context: "$4"
  - pattern: "debezium.([^:]+)<type=connector-metrics, context=([^,]+), server=([^>]+)>([^:]+)"
    name: "debezium_metrics_$4"
    labels:
      plugin: "$1"
      name: "$3"
      context: "$2"
  # end https://github.com/debezium/debezium-examples/blob/main/monitoring/debezium-jmx-exporter/config.yml
```

```yaml
  # mbean = com.mongodb.kafka.connect:connector=mongodb-source,task=source-task-0,type=source-task-metrics:
  - pattern: "com.mongodb.kafka.connect<type=(.+)-task-metrics, connector=(.+), task=(.+)><>(.+): (.+)"
    name: kafka_connect_mongodb_$1_task_metrics_$4
    labels:
      connector: "$2"
      task: "$3"
  # kafka.connect:type=*-task-metrics,*
  # kafka.connect:type=source-task-metrics,connector=*,task=*
  # kafka.connect:type=sink-task-metrics,connector=*,task=*
  # kafka.connect:type=connector-task-metrics,connector=*,task=*
  - pattern: "kafka.connect<type=(.+)-task-metrics, connector=(.+), task=(\\d+)><>(.+): (.+)"
    name: kafka_connect_$1_task_metrics_$4
    labels:
      connector: "$2"
      task: "$3"
  # kafka.connect:type=task-error-metrics,*
  # kafka.connect:type=task-error-metrics,connector=*,task=*
  - pattern: "kafka.connect<type=task-error-metrics, connector=(.+), task=(\\d+)><>([^:]+)"
    name: kafka_connect_task_error_metrics_$3
    labels:
      connector: "$1"
      task: "$2"
  # confluent.replicator:type=confluent-replicator-task-metrics,* : confluent-replicator-task-topic-partition-*: Number Values
  - pattern: "confluent.replicator<type=confluent-replicator-task-metrics, confluent-replicator-(.*)=(.+), confluent-replicator-(.+)=(.+), confluent-replicator-(.+)=(.+), confluent-replicator-(.+)=(.+)><>confluent-replicator-task-topic-partition-(.*): (.*)"
    name: confluent_replicator_task_metrics_$9
    labels:
      $1: "$2"
      $3: "$4"
      $5: "$6"
      $7: "$8"
  # confluent.replicator:type=confluent-replicator-task-metrics,* : Strings
  - pattern: "confluent.replicator<type=confluent-replicator-task-metrics, confluent-replicator-(.*)=(.+), confluent-replicator-(.+)=(.+), confluent-replicator-(.+)=(.+), confluent-replicator-(.+)=(.+)><>(confluent-replicator-destination-cluster|confluent-replicator-source-cluster|confluent-replicator-destination-topic-name): (.*)"
    name: confluent_replicator_task_metrics_info
    value: 1
    labels:
      $1: "$2"
      $3: "$4"
      $5: "$6"
      $7: "$8"
      $9: "$10"
  # "kafka.consumer:type=app-info,client-id=*"
  # "kafka.producer:type=app-info,client-id=*"
  - pattern: "kafka.(.+)<type=app-info, client-id=(.+)><>(.+): (.+)"
    value: 1
    name: kafka_$1_app_info
    labels:
      client_type: $1
      client_id: $2
      $3: $4
    type: UNTYPED
  # "kafka.consumer:type=consumer-metrics,client-id=*, protocol=*, cipher=*"
  # "kafka.consumer:type=type=consumer-fetch-manager-metrics,client-id=*, topic=*, partition=*"
  # "kafka.producer:type=producer-metrics,client-id=*, protocol=*, cipher=*"
  - pattern: "kafka.(.+)<type=(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+)><>(.+):"
    name: kafka_$1_$2_$9
    type: GAUGE
    labels:
      client_type: $1
      $3: "$4"
      $5: "$6"
      $7: "$8"
  # "kafka.consumer:type=consumer-node-metrics,client-id=*, node-id=*"
  # "kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*, topic=*"
  # "kafka.producer:type=producer-node-metrics,client-id=*, node-id=*"
  # "kafka.producer:type=producer-topic-metrics,client-id=*, topic=*"
  - pattern: "kafka.(.+)<type=(.+), (.+)=(.+), (.+)=(.+)><>(.+):"
    name: kafka_$1_$2_$7
    type: GAUGE
    labels:
      client_type: $1
      $3: "$4"
      $5: "$6"
  # "kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*"
  # "kafka.consumer:type=consumer-metrics,client-id=*"
  # "kafka.producer:type=producer-metrics,client-id=*"
  - pattern: "kafka.(.+)<type=(.+), (.+)=(.+)><>(.+):"
    name: kafka_$1_$2_$5
    type: GAUGE
    labels:
      client_type: $1
      $3: "$4"
  - pattern: "kafka.(.+)<type=(.+)><>(.+):"
    name: kafka_$1_$2_$3
    labels:
      client_type: $1
```

## KSQLDB

```yaml
specs:
  metrics:
    prometheus:
      whitelist:
        - "io.confluent.ksql.metrics:*"
        # The two lines below are used to pull the Kafka Client Producer & consumer metrics from KSQL Client.
        # If you care about Producer/Consumer metrics for KSQL, please uncomment 2 lines below.
        # Please note that this increases the scrape duration to about 1 second as it needs to parse a lot of data.
        - "kafka.consumer:*"
        - "kafka.producer:*"
        - "kafka.streams:*"
      blacklist:
        - "io.confluent.ksql.metrics:name=*"
        - kafka.streams:type=kafka-metrics-count
        # This will ignore the admin client metrics from KSQL server and will blacklist certain metrics
        # that do not make sense for ingestion.
        - "kafka.admin.client:*"
        - "kafka.consumer:type=*,id=*"
        - "kafka.consumer:type=*,client-id=*"
        - "kafka.consumer:type=*,client-id=*,node-id=*"
        - "kafka.producer:type=*,id=*"
        - "kafka.producer:type=*,client-id=*"
        - "kafka.producer:type=*,client-id=*,node-id=*"
        - "kafka.streams:type=stream-processor-node-metrics,thread-id=*,task-id=*,processor-node-id=*"
        - "kafka.*:type=kafka-metrics-count,*"
        - "io.confluent.ksql.metrics:type=_confluent-ksql-rest-app-command-runner,*"
        - "io.confluent.ksql.metrics:type=ksql-queries,query-id=*,*"
      rules:
        # "io.confluent.ksql.metrics:type=producer-metrics,key=*,id=*"
        # "io.confluent.ksql.metrics:type=consumer-metrics,key=*,id=*"
        - pattern: io.confluent.ksql.metrics<type=(.+), key=(.+), id=(.+)><>([^:]+)
          name: ksql_$1_$4
          cache: true
          labels:
            key: "$2"
            id: "$3"
        # "io.confluent.ksql.metrics:type=_confluent-ksql-<cluster-id>ksql-engine-query-stats"
        # The below statement parses KSQL Cluster Name and adds a new label so that per cluster data is searchable.
        - pattern: io.confluent.ksql.metrics<type=_confluent-ksql-(.+)ksql-engine-query-stats><>([^:]+)
          name: "ksql_ksql_engine_query_stats_$2"
          cache: true
          labels:
            ksql_cluster: $1
        # "io.confluent.ksql.metrics:type=ksql-queries,ksql_service_id=<cluser-id>,status=_confluent-ksql-query_<query>"
        # The below statement parses KSQL query specific status
        - pattern: "io.confluent.ksql.metrics<type=(.+), ksql_service_id=(.+), status=_confluent-ksql-query_(.+)><>(.+): (.+)"
          value: "1"
          name: ksql_ksql_metrics_$1_$4
          cache: true
          labels:
            ksql_query: $3
            ksql_cluster: $2
            $4: $5
        # io.confluent.ksql.metrics:type=_confluent-ksql-pull-query, ksql_service_id=value, query_plan_type=value, query_routing_type=value, query_source=value
        - pattern: 'io.confluent.ksql.metrics<type=_confluent-ksql-(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+)><>(.+)-detailed-distribution-(\d+):'
          name: ksql_ksql_metrics_$1
          type: GAUGE
          cache: true
          labels:
            $2: $3
            $4: $5
            $6: $7
            $8: $9
            attribute_name: $10
            quantile: "0.$11"
        - pattern: 'io.confluent.ksql.metrics<type=_confluent-ksql-(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+)><>(.+)-detailed-([\w-]+):'
          name: ksql_ksql_metrics_$1
          cache: true
          labels:
            $2: $3
            $4: $5
            $6: $7
            $8: $9
            attribute_name: $10
            value_type: $11
        # kafka.streams:type=stream-processor-node-metrics,processor-node-id=*,task-id=*,thread-id=*
        # kafka.streams:type=stream-record-cache-metrics,record-cache-id=*,task-id=*,thread-id=*
        # kafka.streams:type=stream-state-metrics,rocksdb-state-id=*,task-id=*,thread-id=*
        # kafka.streams:type=stream-state-metrics,rocksdb-state-id=*,task-id=*,thread-id=*
        - pattern: "kafka.streams<type=(.+), thread-id=(.+), task-id=(.+), (.+)=(.+)><>(.+):"
          name: kafka_streams_$1_$6
          type: GAUGE
          cache: true
          labels:
            thread_id: "$2"
            task_id: "$3"
            $4: "$5"
        # kafka.streams:type=stream-task-metrics,task-id=*,thread-id=*
        - pattern: "kafka.streams<type=(.+), thread-id=(.+), task-id=(.+)><>(.+):"
          name: kafka_streams_$1_$4
          type: GAUGE
          cache: true
          labels:
            thread_id: "$2"
            task_id: "$3"
        # kafka.streams:type=stream-metrics,client-id=*
        - pattern: "kafka.streams<type=stream-metrics, (.+)=(.+)><>(state|alive-stream-threads|commit-id|version|application-id): (.+)"
          name: kafka_streams_stream_metrics
          value: "1"
          type: UNTYPED
          cache: true
          labels:
            $1: "$2"
            $3: "$4"
        # kafka.streams:type=stream-thread-metrics,thread-id=*
        - pattern: "kafka.streams<type=(.+), (.+)=(.+)><>([^:]+)"
          name: kafka_streams_$1_$4
          type: GAUGE
          cache: true
          labels:
            $2: "$3"
        # "kafka.consumer:type=app-info,client-id=*"
        # "kafka.producer:type=app-info,client-id=*"
        - pattern: "kafka.(.+)<type=app-info, client-id=(.+)><>(.+): (.+)"
          value: "1"
          name: kafka_$1_app_info
          cache: true
          labels:
            client_type: $1
            client_id: $2
            $3: $4
            type: UNTYPED
        # "kafka.consumer:type=consumer-metrics,client-id=*, protocol=*, cipher=*"
        # "kafka.consumer:type=type=consumer-fetch-manager-metrics,client-id=*, topic=*, partition=*"
        # "kafka.producer:type=producer-metrics,client-id=*, protocol=*, cipher=*"
        - pattern: "kafka.(.+)<type=(.+), (.+)=(.+), (.+)=(.+), (.+)=(.+)><>(.+):"
          name: kafka_$1_$2_$9
          type: GAUGE
          cache: true
          labels:
            client_type: $1
            $3: "$4"
            $5: "$6"
            $7: "$8"
        # "kafka.consumer:type=consumer-node-metrics,client-id=*, node-id=*"
        # "kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*, topic=*"
        # "kafka.producer:type=producer-node-metrics,client-id=*, node-id=*"
        # "kafka.producer:type=producer-topic-metrics,client-id=*, topic=*"
        - pattern: "kafka.(.+)<type=(.+), (.+)=(.+), (.+)=(.+)><>(.+):"
          name: kafka_$1_$2_$7
          type: GAUGE
          cache: true
          labels:
            client_type: $1
            $3: "$4"
            $5: "$6"
        # "kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*"
        # "kafka.consumer:type=consumer-metrics,client-id=*"
        # "kafka.producer:type=producer-metrics,client-id=*"
        - pattern: "kafka.(.+)<type=(.+), (.+)=(.+)><>(.+):"
          name: kafka_$1_$2_$5
          type: GAUGE
          cache: true
          labels:
            client_type: $1
            $3: "$4"
        - pattern: "kafka.(.+)<type=(.+)><>(.+):"
          name: kafka_$1_$2_$3
          cache: true
          labels:
            client_type: $1
```
