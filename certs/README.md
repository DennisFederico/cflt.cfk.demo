# Certificates

## Self-Signed CA

```bash
$ mkdir -p certs/generated

$ openssl genrsa -out certs/generated/InternalCAkey.pem 2048

$ openssl req -x509 -new -nodes \
  -key certs/generated/InternalCAkey.pem \
  -days 365 \
  -out certs/generated/InternalCAcert.pem \
  -subj "/C=ES/ST=VLC/L=VLC/O=Demo/OU=GCP/CN=KafkaCA"
```

## K8s Secret for CA

We will rely on [CFK Certificate autogeneration](https://docs.confluent.io/operator/current/co-network-encryption.html#auto-generated-tls-certificates). To make this work we need to provide the signing CA Cert and Key is a secret for CFK, by default this secret should be called `ca-pair-sslcerts` of Type `tls`

It is advisable to create a Secret RD to manage this secret

```bash
kubectl create secret tls ca-pair-cfk \
--cert=certs/generated/InternalCAcert.pem \
--key=certs/generated/InternalCAkey.pem \
-n confluent --dry-run=client --output yaml > certs/generated/ca-pair-cfk.yaml

kubectl apply -f certs/generated/ca-pair-cfk.yaml
```

## Using annotations to provide CA Secret

```shell
kubectl annotate kraftcontroller kraftcontroller platform.confluent.io/managed-cert-ca-pair-secret="ca-pair-cfk"
kubectl annotate kraftcontroller kraftcontroller platform.confluent.io/managed-cert-duration-in-days=60
kubectl annotate kraftcontroller kraftcontroller platform.confluent.io/managed-cert-renew-before-in-days=30
kubectl annotate kraftcontroller kraftcontroller platform.confluent.io/managed-cert-add-sans="kraft-san.com"


kubectl annotate kafka kafka platform.confluent.io/managed-cert-ca-pair-secret="ca-pair-kafka"
kubectl annotate kafka kafka platform.confluent.io/managed-cert-duration-in-days=60
kubectl annotate kafka kafka platform.confluent.io/managed-cert-renew-before-in-days=30
kubectl annotate kafka kafka platform.confluent.io/managed-cert-add-sans="kafka-san.com"


kubectl annotate schemaregistry schemaregistry platform.confluent.io/managed-cert-ca-pair-secret="ca-pair-cfk"
kubectl annotate schemaregistry schemaregistry platform.confluent.io/managed-cert-add-sans="sr.confluent.demo.com"
```
