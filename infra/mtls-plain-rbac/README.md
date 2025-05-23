# Deploy mTLS+PLAIN+RBAC

This setup uses "fixed" username/password to connect to kafka from the outside and mTLS on the inside listeners, and RBAC authorization backed by a file store.

- mTLS for Internal listener
- PLAIN for External listener
- MDS for RBAC

## Ingress Controller for StaticForHostBasedRouting most useful in Azure or Environments with public IPs restrictions

**IMPORTANT NOTE:** Pay attention there are two intalls, one that targets AZURE and the other for GCP

```shell
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
```

```shell
### USE THIS FOR AZURE
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
    --set controller.publishService.enabled=true \
    --set controller.extraArgs.enable-ssl-passthrough="true" \
    --set controller.service.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-health-probe-request-path"=/healthz \
    --set controller.service.externalTrafficPolicy=Local \
    -n confluent
```

```shell
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
    --set controller.publishService.enabled=true \
    --set controller.extraArgs.enable-ssl-passthrough="true" \
    --set controller.service.annotations."cloud\.google\.com/load-balancer-type"="External" \
    --set controller.service.externalTrafficPolicy=Local \
    -n confluent
```

The external IP is important to set any DNS or `etc/hosts` file

```shell
kubectl get service --namespace confluent ingress-nginx-controller --output wide --watch
```

### Ingresses for Bootstrap, Brokers and Services

This can be applied later, it also includes the Kafka ERP (Embedded Rest Proxy) service (same MDS port).

```shell
kubectl apply -f infra/ingress/bootstrap-kafka.yaml
kubectl apply -f infra/ingress/ingress-kafka.yaml

kubectl apply -f infra/ingress/kafka-erp-service.yaml
kubectl apply -f infra/ingress/ingress-erp.yaml

kubectl apply -f infra/ingress/ingress-services.yaml
```

Remember that bootstrap port will be `443` when using this ingress

`kafka-broker-api-versions --bootstrap-server bootstrap.confluent.demo.com:443 --command-config ./infra/configs/plain-client.properties`

## MDS Key Pair for Token

```shell
openssl genrsa -traditional -out certs/generated/mds-priv-key.pem 2048
openssl rsa -in certs/generated/mds-priv-key.pem -out PEM -pubout -out certs/generated/mds-pub-key.pem

kubectl create secret generic mds-key-pair \
  --from-file=mdsPublicKey.pem=certs/generated/mds-pub-key.pem \
  --from-file=mdsTokenKeyPair.pem=certs/generated/mds-priv-key.pem \
  --namespace confluent \
  --dry-run=client \
  --output yaml >> certs/generated/mds-key-pair.yaml

kubectl apply -f certs/generated/mds-key-pair.yaml
```

## Access Credentials Configuration

### MDS File User Store

Users that login from Control Center and other HTTP Basic service (*infra/secrets/userstore-secrets.txt*) for RBAC without LDAP or OAuth

```text
dfederico:password
kafka:password
schemaregistry:password
connect:password
ksqldb:password
controlcenter:password
```

```shell
kubectl create secret generic userstore-secret \
  --from-file=userstore.txt=infra/secrets/userstore-secret.txt \
  --namespace confluent \
  --dry-run=client \
  --output yaml >> infra/secrets/userstore-secret.yaml

kubectl apply -f infra/secrets/userstore-secret.yaml
```

### Kafka PLAIN Users

This are the users that can login using PLAIN, but subject to RBAC anyway

The configuration file is a json file with user and field and password as value (*infra/secrets/kafka-users.json*)

```json
{
    "dfederico": "password",
    "a_principal": "password"
}
```

```shell
kubectl create secret generic kafka-users \
  --from-file=plain-users.json=infra/secrets/kafka-users.json \
  --dry-run=client --output yaml > infra/secrets/kafka-users.yaml

kubectl apply -f infra/secrets/kafka-users.yaml
```

### Services BASIC Authentication

For HTTP/Rest APIs of services like SchemaRegistry, Connect, etc, we will use BASIC as Authentication mechanism, authorization should still be delegated to RBAC
The file that needs to be provided is a list of `principal: password, role`

```text
dfederico: password, admin
a_principal: password, admin
connect: password, admin
ksqldb: password, admin
controlcenter: password, admin
```

Assuming (*/infra/secrets/basic-users.txt*)

```shell
kubectl create secret generic basic-users \
  --from-file=basic.txt=infra/secrets/basic-users.txt \
  --namespace confluent \
  --dry-run=client --output yaml > infra/secrets/basic-users.yaml

kubectl apply -f infra/secrets/basic-users.yaml
```

### BASIC Auth between services

This handles the authentication across services like CONNECT->SR
The file content is for a single user, in a properties format with `username` and `password` as properties

Assuming (*/infra/secrets/connect-basic-creds.txt*)

```shell
kubectl create secret generic connect-basic-creds \
  --from-file=basic.txt=infra/secrets/connect-basic-creds.txt \
  --namespace confluent \
  --dry-run=client --output yaml > infra/secrets/connect-basic-creds.yaml

kubectl apply -f infra/secrets/connect-basic-creds.yaml

kubectl create secret generic ksqldb-basic-creds \
  --from-file=basic.txt=infra/secrets/ksqldb-basic-creds.txt \
  --namespace confluent \
  --dry-run=client --output yaml > infra/secrets/ksqldb-basic-creds.yaml

kubectl apply -f infra/secrets/ksqldb-basic-creds.yaml

kubectl create secret generic controlcenter-basic-creds \
  --from-file=basic.txt=infra/secrets/controlcenter-basic-creds.txt \
  --namespace confluent \
  --dry-run=client --output yaml > infra/secrets/controlcenter-basic-creds.yaml

kubectl apply -f infra/secrets/controlcenter-basic-creds.yaml


## KSQLDB CANNOT USE mTLS TO CONNECT WITH MDS
kubectl create secret generic ksqldb-bearer-creds \
  --from-file=bearer.txt=infra/secrets/ksqldb-basic-creds.txt \
  --namespace confluent \
  --dry-run=client --output yaml > infra/secrets/ksqldb-bearer-creds.yaml

kubectl apply -f infra/secrets/ksqldb-bearer-creds.yaml
```

### Control Center Authentication

Control center will pass the authentication credentials via MDS

## RoleBindings

Remember to create the rolebinding for the user you are testing below.
See [client-rb.yaml](./client-rb.yaml)

```shell
kubectl apply -f infra/configs/client-rb.yaml
```

## Monitoring (Prometheus / Grafana)

Install Prometheus Operator if needed

```shell
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install prometheus prometheus-community/prometheus \
 --set alertmanager.persistentVolume.enabled=false \
 --set server.persistentVolume.enabled=false \
 --namespace monitoring \
 --create-namespace
```

To access prometheus internally `prometheus-server.monitoring.svc.cluster.local` at port 80

```shell
export PROMETHEUS_POD=$(kubectl get pods --namespace monitoring -l "app.kubernetes.io/name=prometheus,app.kubernetes.io/instance=prometheus" -o jsonpath="{.items[0].metadata.name}")
kubectl --namespace monitoring port-forward $PROMETHEUS_POD 9090
```

Or deploy an ingress (provided below after grafana install)

Install Grafana

```shell
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm upgrade --install grafana grafana/grafana --namespace monitoring
```

### ALTERNATIVE install including default prometheus datasource

Use the following helm values to configure the datasource (`infra/monitoring/grafana-values.yaml`)

```yaml
datasources:
  datasources.yaml:
    apiVersion: 1
    datasources:
      - name: Prometheus
        type: prometheus
        url: http://prometheus-server.monitoring.svc.cluster.local
        access: proxy
        isDefault: true
## This allows for dashboards to be created in configmaps and then labeled for auto-load
sidecar:  
  dashboards:
    enabled: true
    label: grafana_dashboard
    labelValue: "true"
```

```shell
helm upgrade --install grafana grafana/grafana --namespace monitoring \
  --values infra/monitoring/grafana-values.yaml
```

## External access via port-foward

```shell
## GET 'admin' PASSWORD
kubectl get secret --namespace monitoring grafana \
  -o jsonpath="{.data.admin-password}" | base64 --decode ; echo

## OPEN PORT-FORWARD
kubectl port-forward \
  $(kubectl get pods -n monitoring -l app.kubernetes.io/name=grafana,app.kubernetes.io/instance=grafana -o name) \
  3000 --namespace monitoring
```

Create a Prometheus datasource, remember that the service lies inside k8s (`prometheus-server.monitoring.svc.cluster.local`)

### Ingress for monitoring

For convenience an ingress is provided in `infra/ingress/ingress-monitoring.yaml`
NOTE: Resolution by the ingress might fail or redirect to controlcenter, in such cases use an incognito window

## Prometheus exporter metrics

When deploying Confluent Platform with Confluent for Kubernetes, the default Prometheus JMX exporter configuration can be overridden with the configuration necessary for this project.

The following metrics configuration can be added to the Custom Resource for a Confluent Platform component:

```yaml
spec:
  metrics:
    prometheus:
      whitelist:
        # copy the whitelistObjectNames section from the jmx-exporter yaml configuration for the component.
      blacklist:
        # copy the blacklistObjectNames section from the jmx-exporter yaml configuration for the component.
      rules:
        # copy the rules section from the jmx-exporter yaml configuration for the component.
```

## Grafana Dashboards

Typical dashboards are provided by Confluent PS at [JMX Monitoring Stack - CFK](https://github.com/confluentinc/jmx-monitoring-stacks/tree/main/jmxexporter-prometheus-grafana/cfk)

Mind that these dashboards need to be converted to include the namespace of the cluster. See [update-dashboards.sh](https://github.com/confluentinc/jmx-monitoring-stacks/blob/main/jmxexporter-prometheus-grafana/cfk/update-dashboards.sh)

```shell
kubectl create configmap grafana-kraft-cluster \
  --from-file=infra/monitoring/dashboards/kraft.json \
  --namespace monitoring
kubectl label configmap grafana-kraft-cluster grafana_dashboard="true" --namespace monitoring

kubectl create configmap grafana-kafka-cluster \
  --from-file=infra/monitoring/dashboards/kafka-cluster.json \
  --namespace monitoring
kubectl label configmap grafana-kafka-cluster grafana_dashboard="true" --namespace monitoring
```






## Tests

### Outside

Accessing from the outside via ingres...

Assume a properties file for Kafka clients with the following format (or similar)

```conf
security.protocol=SASL_SSL
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username='dfederico' password='password';
sasl.mechanism=PLAIN
ssl.truststore.type=PEM
ssl.truststore.location=<ABSOLUTE_PATH>/InternalCAcert.pem
```

```shell
## WHEN USING NODEPORT/STATIC/ROUTES THE PORT IS USUALLY 443 INSTEAD OF 9092 (LOADBALANCER)

kafka-topics --bootstrap-server bootstrap.confluent.demo.com:443 \
  --command-config ./infra/configs/plain-client.properties \
  --list

# AND/OR

kafka-broker-api-versions --bootstrap-server bootstrap.confluent.demo.com:443 \
--command-config ./infra/configs/plain-client.properties
```

### ///TODO Confirm CONFLUENT CLI

## TEST mTLS (KAFKA)

From inside the cluster

Assume a certificate created for a user using the same CA in the Kafka Truststore.

Set the CN to the user you are testing

```shell
openssl genpkey -algorithm RSA -out certs/generated/client.key
openssl req -new -key certs/generated/client.key -out certs/generated/client.csr -subj "/CN=dfederico"
openssl x509 -req -in certs/generated/client.csr -CA certs/generated/InternalCAcert.pem -CAkey certs/generated/InternalCAkey.pem -CAcreateserial -out certs/generated/client.pem -days 30 -sha256

openssl x509 -in certs/generated/client.pem -noout -text
```

For mTLS you need to provide a container with Cert and Key (p12)

```shell
openssl pkcs12 -export -in certs/generated/client.pem -inkey certs/generated/client.key -out certs/generated/client.p12
```

Properties for connection [mts-client.properties](secrets/mtls-client.properties)
**NOTE:** Trustore/Keystore path is usually absolute when dealing with kafka CLI

```properties
security.protocol=SSL
ssl.keystore.location=client.p12
ssl.keystore.type=PKCS12
ssl.keystore.password=<your password>
ssl.key.password=<your password>
ssl.truststore.type=PEM
ssl.truststore.location=
```

```shell
$ kafka-topics --bootstrap-server kafka.confluent.demo.com:9093 \
   --command-config infra/auth/secrets/mtls-client.properties \
   --list
```

## Test OAuth Kafka

Properties for connection [oauth-client.properties](secrets/oauth-client.properties)
**NOTE:** Trustore/Keystore path is usually absolute when dealing with kafka CLI

```properties
security.protocol=SASL_SSL
sasl.mechanism=OAUTHBEARER
ssl.truststore.type=
ssl.truststore.location=
ssl.truststore.password=
sasl.login.callback.handler.class=org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerLoginCallbackHandler
sasl.oauthbearer.token.endpoint.url=http://keycloak/realms/sso_test/protocol/openid-connect/token
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
   clientId="" \
   clientSecret="";
```

```shell
$ kafka-topics --bootstrap-server kafkaoauth.confluent.demo.com:9092 \
  --command-config infra/auth/secrets/oauth-client.properties \
  --list
```

## Schema Registry

```shell
curl --cert-type P12 --cert /Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/certs/generated/client.p12:secure \
    --key /Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/certs/generated/client.key \
    --cacert /Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/certs/generated/InternalCAcert.pem \
    --url https://sr.confluent.demo.com/subjects \
    --header "Content-Type: application/json"

curl -u dfederico:password \
    --url https://schemaregistry.confluent.demo.com/subjects \
    --cacert /Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/certs/generated/InternalCAcert.pem
```

```shell
kafka-avro-console-producer --bootstrap-server kafka.confluent.demo.com:9092 --topic test \
--producer.config /Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/infra/auth/secrets/ext-client.properties \
--property schema.registry.url=https://sr.confluent.demo.com \
--property schema.registry.ssl.truststore.type=PEM \
--property schema.registry.ssl.truststore.location=/Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/certs/generated/InternalCAcert.pem \
--property schema.registry.ssl.keystore.type=PKCS12 \
--property schema.registry.ssl.keystore.location=/Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/certs/generated/client.p12 \
--property schema.registry.ssl.keystore.password=secure \
--property value.schema.id=1


kafka-avro-console-consumer --bootstrap-server kafka.confluent.demo.com:9092 --topic test \
--consumer.config /Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/infra/auth/secrets/ext-client.properties \
--property schema.registry.url=https://sr.confluent.demo.com \
--property schema.registry.ssl.truststore.type=PEM \
--property schema.registry.ssl.truststore.location=/Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/certs/generated/InternalCAcert.pem \
--property schema.registry.ssl.keystore.type=PKCS12 \
--property schema.registry.ssl.keystore.location=/Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/certs/generated/client.p12 \
--property schema.registry.ssl.keystore.password=secure \
--from-beginning
```
