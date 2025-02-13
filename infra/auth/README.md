# Start Services (KeyCloak)

Docker compose or K8s deployment in [keycloak](keycloak/) folder.

## Tests KeyCloak

Assuming `keycloak` is added to `etc/hosts` or any other name to the DNS.

```shell
curl --url http://keycloak/realms/sso_test/.well-known/openid-configuration | jq
```

Assuming the [demo](keycloak/realms/demo-realm.json) realm configuration.

```shell
curl --request POST \
  --url http://keycloak/realms/sso_test/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'client_id=kafka_client' \
  --data-urlencode 'client_secret=VwQ0h755Ni8s595ZY7XmOgMD8BWTAWri' \
  | jq
```

Decode the token, it may lack a closing final `=` as tokens are padded in blocks of 4 bytes (note the use of awk)

```shell

### IMPORTANT... The below code has a hack that adds one padding symbol (=) to terminate the json.
### Tokens are 4 bytes and you might need to ad up to three (3) depending on the toker
### Othewise 'jq' won't pretty print the decoded token

curl -s --request POST \
  --url http://keycloak/realms/sso_test/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'client_id=kafka_client' \
  --data-urlencode 'client_secret=VwQ0h755Ni8s595ZY7XmOgMD8BWTAWri' \
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

### MDS File User Store

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

### MDS OIDC Provider

This is required for [mTLS+OAuth+RBAC](./platform-mtls-oauth-rbac.yaml). Used to validate Tokens presented by Kafka Clients that access Kafka

```shell
kubectl create secret generic oidc-creds \
  --from-file=oidcClientSecret.txt=infra/auth/secrets/oidc-creds.txt \
  --namespace confluent \
  --dry-run=client \
  --output yaml >> infra/auth/secrets/oidc-creds.yaml

kubectl apply -f infra/auth/secrets/oidc-creds.yaml
```

## RoleBindings

Remember to create the rolebinding for the user you are testing below.
See [client-rb.yaml](./client-rb.yaml)

## TEST mTLS (KAFKA)

Assume a certificate created for a user using the same CA in the Kafka Truststore.

Set the CN to the user you are testing

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

## KSQLDB (Bearer) - Work In Progress

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

