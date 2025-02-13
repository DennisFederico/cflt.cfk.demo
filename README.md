# Quick Confluent Platfrom DEMO using CFK

Using a modified version (w/Kraft) of the base [CFK deployment](https://github.com/DennisFederico/cfk-sandbox/tree/main/notls-noauth) with No AuthZ and No TLS.

## CFK 2.8 (0.1145.6) Basic Installation

CFK 2.10.0 (CP 7.8.0) [Release Notes](https://docs.confluent.io/operator/2.10/release-notes.html)

Kubernetes versions 1.25 - 1.31 (OpenShift 4.11 - 4.17)

### Namespace and Operator

```shell
kubectl create namespace confluent
```

```shell
helm upgrade --install cfk-operator \
  confluentinc/confluent-for-kubernetes \
  --version  0.1145.6 \
  --namespace confluent
```

### About CRDs

[CFK 2.10 API](https://docs.confluent.io/operator/2.10/co-api.html)

```shell
kubectl get crds

kubectl explain <crd>
```

### Create a Persistent Storage Class

See. [Persistent storage volumes](https://docs.confluent.io/operator/current/co-storage.html#persistent-storage-volumes) in the Confluent documentation.

```shell
kubectl apply -f infra/storageclass.yaml
```

### Deploy Cluster

```shell
# Kraft
kubectl apply -f infra/platform-no-auth.yaml -l component=kraft

# Kafka
kubectl apply -f infra/platform-no-auth.yaml -l component=kafka

# Scema Registry
kubectl apply -f infra/platform-no-auth.yaml -l component=sr

# Kafka Connect
kubectl apply -f infra/platform-no-auth.yaml -l component=connect

# ksqlDB
kubectl apply -f infra/platform-no-auth.yaml -l component=ksqldb

# Control Center
kubectl apply -f infra/platform-no-auth.yaml -l component=c3

# Rest Proxy
kubectl apply -f infra/platform-no-auth.yaml -l component=restproxy
```

### Deploy Resources via CRD

Example of CRD managed resource

- Topic

```shell
kubectl apply -f resources/topic-person.yaml
```

- Schema

```shell
kubectl apply -f resources/schema-person.yaml
```

### Quick Perf-Test App

```shell
kubectl apply -f resources/app-internal-noauth.yaml

kubectl logs -f pod/perf-producer-app

kubectl logs -f pod/perf-consumer-app
```

### Enable Extenal Access (LoadBalancer)

This way we access Control Center without kubectl port-forward, and the rest of the services.

Check the differences on [platform-no-auth-ext.yaml](infra/platform-no-auth-ext.yaml)

## Usage Demos

### Simple Operations with KSQLDB

Mainly to confirm the correct deployment of the external access, since C3 won't proxy the communication to ksqldb for push queries (these open a socket between the cluster and the client browser).

Remember to DNS the IPs exposed by the LBs or add them to your /etc/hosts

- Create Stream with a specific format and topic

```sql
CREATE STREAM users_demo (
  id INTEGER KEY, 
  gender STRING, 
  name STRING, 
  age INTEGER
) WITH (
  kafka_topic='users_demo', 
  partitions=1, 
  value_format='JSON_SR');
```

- Insert Some Data

```sql
INSERT INTO users_demo (id, gender, name, age) VALUES (0, 'female', 'Danella', 30);
INSERT INTO users_demo (id, gender, name, age) VALUES (1, 'male', 'Dennis', 33);
INSERT INTO users_demo (id, gender, name, age) VALUES (42, 'female', 'Aitana', 25);
```

- Check the content of the STREAM (reminder: set `auto.offset.reset` to `Earliest`)

```sql
select * from USERS_DEMO EMIT CHANGES;

select * from USERS_DEMO LIMIT 1;
```

### Source Connect (Datagen + ksqlDB)

- Deploy Datagen Connector as a CRD

```shell
kubectl apply -f resources/datagen-pizza.yaml
```

- Queries...

Stream over the topic fields are discovered from the schema

```sql
CREATE STREAM PIZZA_ORDERS WITH (KAFKA_TOPIC='pizza-orders', VALUE_FORMAT='AVRO');
```

Flatten the order lines

```sql
CREATE STREAM PIZZA_ORDERS_LINES WITH (KAFKA_TOPIC='pizza_orders_lines', VALUE_FORMAT='AVRO', REPLICAS=3) AS
SELECT STORE_ID, 
       DATE, 
       EXPLODE(ORDER_LINES)->PRODUCT_ID,
       EXPLODE(ORDER_LINES)->CATEGORY,
       EXPLODE(ORDER_LINES)->QUANTITY,
       EXPLODE(ORDER_LINES)->NET_PRICE
FROM PIZZA_ORDERS EMIT CHANGES;
```

Create a Table with the TOTAL by Product (NOTE WHAT HAPPEN WHEN KEY FORMAT IS NOT DEFINED)

```sql
CREATE TABLE PIZZA_SALES_BY_PRODUCT WITH (KAFKA_TOPIC='pizza_sales_by_product', VALUE_FORMAT='AVRO', KEY_FORMAT='AVRO', REPLICAS=3) AS
SELECT
    PRODUCT_ID,
    SUM(QUANTITY) AS SOLD_QUANTITY,
    SUM(NET_PRICE) AS SOLD_NET_PRICE
FROM PIZZA_ORDERS_LINES
GROUP BY PRODUCT_ID;
```

Create a Table of Product Names (Dimension Table)

```sql
CREATE TABLE PIZZA_PRODUCT_CATEGORY WITH (KAFKA_TOPIC='pizza_product_category', FORMAT='AVRO', REPLICAS=3) AS
SELECT
    PRODUCT_ID,
    LATEST_BY_OFFSET(CATEGORY) AS CATEGORY
FROM PIZZA_ORDERS_LINES
GROUP BY PRODUCT_ID
EMIT CHANGES;
```

Join the table-table

```sql
CREATE TABLE PIZZA_SALES_BY_CATEGORY WITH (KAFKA_TOPIC='pizza_sales_by_category', FORMAT='AVRO') AS
SELECT
  p.CATEGORY,
  COALESCE(p.CATEGORY, 'Unknown') AS CAT,
  SUM(s.SOLD_QUANTITY) AS SOLD_QUANTITY,
  SUM(s.SOLD_NET_PRICE) AS SOLD_NET_PRICE
FROM PIZZA_SALES_BY_PRODUCT s
LEFT JOIN PIZZA_PRODUCT_CATEGORY p ON s.PRODUCT_ID = p.PRODUCT_ID
GROUP BY CATEGORY
EMIT CHANGES;
```

Now you can query the table as a Pull query to get the latest state (Sales by Category)

```sql
SELECT * FROM PIZZA_SALES_BY_CATEGORY;
```

Or as a Push Query to continuosly receive the updates to the table

```sql
SELECT * FROM PIZZA_SALES_BY_CATEGORY EMIT CHANGES;
```

### End-to-End Pipeline

This pipeline starts with fetching changes from a local [mysql](mysql/docker-compose.yaml) database using a [Debezium CDC Source connector](https://debezium.io/documentation/reference/2.4/connectors/mysql.html), Changes are streamed into a topic per table where transformations are applied to the data (both via SMTs on the connector and use ksqlDB), a KStream application in the pipeline monitors acoount balances that become negative to emit an event to notify the account holder, these events are captured by a HTTP Sink Connector that calls an Generic HTTP endpoint locally.

#### Database

Using Docker Compose in [mysql](/mysql/) folder

A [Notebook with queries](mysql/queries.mysql-notebook) is available, and the [initial dataset](mysql/config/init.sql), along with permisions for the connect user to read the binlog.

#### CDC Surce Connector

Using the Debezium CDC for mySQL. The configuration provided a a JSON that can be deployed using C3 or the Connect REST Endpoint.

But before deploying the connector, it is required to install the plugin on the cluster

```shell
kubectl apply -f infra/connect-with-plugins.yaml
```

Then use latest example of the Connector (attempt 3)

- [attempt 1](resources/connector_mysql_cdc_1_config.json) - Plain CDC without SMT's or cutomizations
- [attempt 2](resources/connector_mysql_cdc_2_config.json) - Parameterize who decimals are converted/serialized
- [attempt 3](resources/connector_mysql_cdc_3_config.json) - Builds upon attempt 2 and keeps only the final state of the record (removes the CDC meta and "before") using a SMT.

**IMPORTANT**: Modify the configuration to the domain name or IP where the database is running, and make sure the firewall is open to access database ports.

#### HTTP Receiving Endpoint

Build the image from [httpsink/Dockerfile](httpsink/Dockerfile), deploy the docker compose and monitor the logs of the container. The application just output the content received on port 5000 and whether or not is a valid JSON.

#### HTTP Sink Connector

This connector configuration provided as JSON [here](resources/connector_HttpSink_config.json), needs to be modified according on where the HTTP receiver has been deployed.

**IMPORTANT**: Modify the configuration to the domain name or IP where the HTTP Endpoint is running, and make sure the firewall is open (port 5000).

#### KStream to monitor Accounts with Negative Balances

The KStream (Java Application) that monitor and filter Balance change events to emit a notification is available [here](/kstreams/NegativeBalances/).

The application is configured with a [properties file](kstreams/NegativeBalances/src/main/resources/stream.properties) that needs to be provided as an argument when running the application.

#### ksqlDB Queries

These queries prepare the data from the CDC to ksqlDB

```sql
CREATE STREAM OVERDRAFT WITH (KAFKA_TOPIC='notifications-topic', FORMAT='AVRO');

CREATE STREAM CUSTOMER_STREAM WITH (KAFKA_TOPIC='cdc3.accounts.customers', FORMAT='AVRO');

CREATE TABLE CUSTOMERS_TABLE WITH (KAFKA_TOPIC='CUSTOMERS_TABLE', FORMAT='AVRO') AS
SELECT ID AS CUSTOMER_ID,
LATEST_BY_OFFSET(first_name) + ' ' + LATEST_BY_OFFSET(last_name) as CUSTOMER_NAME,
LATEST_BY_OFFSET(EMAIL) AS EMAIL
FROM CUSTOMER_STREAM
GROUP BY ID
EMIT CHANGES;

CREATE STREAM OVERDRAFT_NOTIFICATION WITH (KAFKA_TOPIC='overdraft.notification', FORMAT='AVRO') AS
SELECT C.CUSTOMER_ID,
       C.CUSTOMER_NAME AS CUSTOMER_NAME,
       C.EMAIL AS CUSTOMER_EMAIL,
       O.ID AS ACCT_ID,
       O.BALANCE AS ACCT_BALANCE
FROM OVERDRAFT O
LEFT JOIN CUSTOMERS_TABLE C ON O.CUSTOMER_ID = C.CUSTOMER_ID
EMIT CHANGES;
```

## Miscellanous

## Connect Logs

Log level on Connect Workers and Connectors can be changed at runtime. 

- The following commands show the current log level

```shell
curl -Ss http://connect.confluent.demo.com/admin/loggers | jq

curl -Ss http://connect.confluent.demo.com/admin/loggers/org.apache.kafka.connect.runtime.WorkerSourceTask | jq
```

- To change the level of log for a Worked

```shell
curl -s -X PUT -H "Content-Type:application/json" \
http://connect.confluent.demo.com/admin/loggers/org.apache.kafka.connect.runtime.WorkerSourceTask \
-d '{"level": "TRACE"}' | jq '.'
```

- To change the log level of a specific connector, use the type/class of the connector

```shell
curl -s -X PUT -H "Content-Type:application/json" \
http://connect.confluent.demo.com/admin/loggers/io.debezium.connector.mysql \
-d '{"level": "DEBUG"}' | jq '.'
```

## CLEAN SR SUBJECTS

[Schema Registry REST API](https://docs.confluent.io/platform/current/schema-registry/develop/api.html)

```shell
curl http://sr.confluent.demo.com/subjects | jq
curl http://sr.confluent.demo.com/subjects?deleted=true | jq

curl -X DELETE http://sr.confluent.demo.com/subjects/<subject> | jq
curl -X DELETE http://sr.confluent.demo.com/subjects/<subject>?permanent=true | jq
```

## Blog about CSFLE in Connect

[Self-Managed Connector CSFLE](https://docs.confluent.io/platform/current/connect/manage-csfle.html)

## Customize Operator (on Deployment) via Helm

See. [Deploy Customized Operator](https://docs.confluent.io/operator/current/co-deploy-cfk.html#deploy-customized-co)

### Option 1. Use values.yaml

```shell
mkdir -p .ignore-cfk

helm pull confluentinc/confluent-for-kubernetes \
  --untar \
  --untardir=.ignore-cfk \
  --namespace confluent
```

Create a copy of the `values.yaml` file with the desired configuration and apply.

```shell
helm upgrade --install cfk-operator \
  confluentinc/confluent-for-kubernetes \
  --values .ignore-cfk/values-copy.yaml \
  --namespace confluent
```

### Option 2. Use Argument Flags

```shell
helm upgrade --install cfk-operator \
  confluentinc/confluent-for-kubernetes \
  --set managedCerts.enabled=true \
  --set managedCerts.caCertificate.secretRef=ca-pair-cfk \
  --set managedCerts.sans='*.confluent.demo.com' \
  --namespace confluent
```
