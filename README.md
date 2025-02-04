# Quick Confluent Platfrom DEMO using CFK

Using a modified version (w/Kraft) of the base [CFK deployment](https://github.com/DennisFederico/cfk-sandbox/tree/main/notls-noauth) with No AuthZ and No TLS.

## CFK 2.8 (0.1145.6) Installation

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

### Install a Persistent Storage Class

```shell
kubectl apply -f infra/storage-class.yaml
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

### Create a Topic

```shell
kubectl apply -f resources/topic-person.yaml
```

### Deploy Schema

```shell
kubectl apply -f resources/schema-person.yaml
```

### Quick Perf-Test App

```shell
kubectl apply -f resources/app-internal-noauth.yaml

kubectl logs -f pod/perf-producer-app

kubectl logs -f pod/perf-consumer-app
```

## Enable Loadbalancer (External Interface)

### ksqlDB

#### Simple Operations

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

#### KSQL Pizza Demo

- Connector Datagen

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

Create a Table of Product Names?

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

### Database (OnPrem)

### HTTP Enpoint (OnPrem)
