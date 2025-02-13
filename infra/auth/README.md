# Start Services (KeyCloak)

Docker compose in [keycloak](keycloak/) folder.

## Tests KeyCloak

Assuming `keycloak` is added to `etc/hosts` or any other name to the DNS.

```shell
curl --url http://keycloak:8080/realms/kafka-authbearer/.well-known/openid-configuration | j
```

Assuming the [simple](keycloak/realms/simple-realm.json) realm configuration.

```shell
curl --request POST \
  --url http://keycloak:8080/realms/kafka-authbearer/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'client_id=kafka_client' \
  --data-urlencode 'client_secret=kafka_client_secret' \
  | jq
```

Decode the token, it may lack a closing final `=` as tokens are padded in blocks of 4 bytes (note the use of awk)

```shell
curl -s --request POST \
  --url http://keycloak:8080/realms/kafka-authbearer/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'client_id=kafka_client' \
  --data-urlencode 'client_secret=kafka_client_secret' \
  | jq -r .access_token \
  | cut -d'.' -f2 \
  | awk '{print $1"="}' \
  | base64 -d | tr -d '\n' | jq .
```

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

### MDS User Store

Users that login from Control Center and other HTTP Basic service

```text
dfederico:password
ksqldb:password
some_user:some_password
```

```shell
kubectl create secret generic userstore-secret \
  --from-file=userstore.txt=infra/auth/secrets/userstore-secret.txt \
  --namespace confluent \
  --dry-run=client \
  --output yaml >> infra/auth/secrets/userstore-secret.yaml

kubectl apply -f infra/auth/secrets/userstore-secret.yaml
```

## TEST mTLS (KAFKA)

Assume a certificate created for a user using the same CA in the Kafka Truststore.

```shell
openssl genpkey -algorithm RSA -out certs/generated/client.key -aes256
openssl req -new -key certs/generated/client.key -out certs/generated/client.csr
openssl x509 -req -in certs/generated/client.csr -CA certs/generated/InternalCAcert.pem -CAkey certs/generated/InternalCAkey.pem -CAcreateserial -out certs/generated/client.pem -days 30 -sha256

openssl x509 -in certs/generated/client.pem -noout -text
```

For mTLS you need to provide a container with Cert and Key (p12)

```shell
openssl pkcs12 -export -in certs/generated/client.pem -inkey certs/generated/client.key -out certs/generated/client.p12
```

Create a truststore with the CA presented by the broker

```shell

```

Properties for connection

```properties
security.protocol=SSL
ssl.keystore.location=client.p12
ssl.keystore.type=PKCS12
ssl.keystore.password=<your password>
ssl.key.password=<your password>
ssl.truststore.type=PEM
ssl.truststore.location=
```

## Schema Registry

```shell
curl --cert-type P12 --cert /Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/certs/generated/client.p12:secure \
    --key /Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/certs/generated/client.key \
    --cacert /Users/dfederico/projects/cflt-sandbox/cflt.cfk.demo/certs/generated/InternalCAcert.pem \
    --url https://sr.confluent.demo.com/subjects \
    --header "Content-Type: application/json"

curl -u dfederico:password \
    --url https://sr.confluent.demo.com/subjects \
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

## KSQLDB (Bearer)

```shell
kubectl create secret generic ksqldb-bearer \
  --from-file=bearer.txt=infra/auth/secrets/ksqldb-bearer.txt \
  --namespace confluent \
  --dry-run=client \
  --output yaml >> infra/auth/secrets/ksqldb-bearer.yaml

kubectl apply -f infra/auth/secrets/ksqldb-bearer.yaml
```

```shell
kubectl create secret generic ksqldb-basic \
  --from-file=basic.txt=infra/auth/secrets/ksqldb-basic.txt \
  --namespace confluent \
  --dry-run=client \
  --output yaml >> infra/auth/secrets/ksqldb-basic.yaml

kubectl apply -f infra/auth/secrets/ksqldb-basic.yaml
```

```shell
kubectl create secret generic c3-ksqldb-basic \
  --from-file=basic.txt=infra/auth/secrets/c3-ksqldb-basic.txt \
  --namespace confluent \
  --dry-run=client \
  --output yaml >> infra/auth/secrets/c3-ksqldb-basic.yaml

kubectl apply -f infra/auth/secrets/c3-ksqldb-basic.yaml
```

```shell
kubectl create secret generic oidc-creds \
  --from-file=oidcClientSecret.txt=infra/auth/secrets/oidc-creds.txt \
  --namespace confluent \
  --dry-run=client \
  --output yaml >> infra/auth/secrets/oidc-creds.yaml

kubectl apply -f infra/auth/secrets/oidc-creds.yaml
```
