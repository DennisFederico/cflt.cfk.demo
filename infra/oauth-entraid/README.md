# Deploy mTLS+PLAIN+RBAC

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


### Control Center Authentication (JAAS)

## RoleBindings

Remember to create the rolebinding for the user you are testing below.
See [client-rb.yaml](./client-rb.yaml)

```shell
kubectl apply -f infra/configs/client-rb.yaml
```

## TEST mTLS (KAFKA)

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

## PLAIN AUTH SETUP

This setup uses "fixed" username/password for authentication and RBAC using file store.

Conveniently avoids login to "HTTP" Services using mTLS (ie. SR, Connect, etc...)

### Platform

See. [platform-plain-rbac.yaml](./platform-plain-rbac.yaml)

```shell




kubectl apply -f infra/auth/secrets/connect-basic.yaml

kubectl create secret generic connect-plain \
  --from-file=plain.txt=infra/auth/secrets/connect-plain.txt \
  --namespace confluent \
  --dry-run=client --output yaml > infra/auth/secrets/connect-plain.yaml

kubectl apply -f infra/auth/secrets/connect-plain.yaml

kubectl create secret generic connect-bearer \
  --from-file=bearer.txt=infra/auth/secrets/connect-plain.txt \
  --namespace confluent \
  --dry-run=client --output yaml > infra/auth/secrets/connect-bearer.yaml

kubectl apply -f infra/auth/secrets/connect-bearer.yaml

kubectl create secret generic ksqldb-plain \
  --from-file=basic.txt=infra/auth/secrets/ksqldb-plain.txt \
  --namespace confluent \
  --dry-run=client --output yaml > infra/auth/secrets/ksqldb-plain.yaml

kubectl apply -f infra/auth/secrets/ksqldb-plain.yaml

kubectl create secret generic c3-plain \
  --from-file=basic.txt=infra/auth/secrets/c3-plain.txt \
  --namespace confluent \
  --dry-run=client --output yaml > infra/auth/secrets/c3-plain.yaml

kubectl apply -f infra/auth/secrets/c3-plain.yaml
```

Bootstrap services and ingresses for kafka and services

```shell
kubectl apply -f infra/ingress/bootstrap-kafka.yaml
kubectl apply -f infra/ingress/ingress-kafka.yaml 
kubectl apply -f infra/ingress/ingress-services.yaml 
```

Adjust DNS or `etc/hotst`

```shell
kubectl get service --namespace confluent ingress-nginx-controller --output wide --watch
```

TEST

```shell
kafka-topics --bootstrap-server bootstrap.confluent.demo.com:443 \
    --command-config infra/auth/secrets/mtls-client.properties \
    --list
```

## CC Root CA as Secret

```shell
keytool -import -trustcacerts \
  -alias letsencryptroot \
  -file infra/auth/secrets/isrgrootx1.pem \
  -keystore infra/auth/secrets/cc_truststore.jks
```

```shell
kubectl create secret generic cc-root-jks \
  --from-file=cc_truststore.jks=infra/auth/secrets/cc_truststore.jks \
  --namespace confluent \
  --dry-run=client --output yaml > infra/auth/secrets/cc-root-jks.yaml

kubectl apply -f infra/auth/secrets/cc-root-jks.yaml
```

```shell
kubectl create secret generic cc-root-pem \
  --from-file=cc-ca.pem=infra/auth/secrets/isrgrootx1.pem \
  --namespace confluent \
  --dry-run=client --output yaml > infra/auth/secrets/cc-root-pem.yaml

kubectl apply -f infra/auth/secrets/cc-root-pem.yaml
```



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