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

kubectl annotate controlcenter controlcenter platform.confluent.io/managed-cert-add-sans="kraft-san.com"


kubectl annotate kafka kafka platform.confluent.io/managed-cert-ca-pair-secret="ca-pair-kafka"
kubectl annotate kafka kafka platform.confluent.io/managed-cert-duration-in-days=60
kubectl annotate kafka kafka platform.confluent.io/managed-cert-renew-before-in-days=30
kubectl annotate kafka kafka platform.confluent.io/managed-cert-add-sans="kafka-san.com"


kubectl annotate schemaregistry schemaregistry platform.confluent.io/managed-cert-ca-pair-secret="ca-pair-cfk"
kubectl annotate schemaregistry schemaregistry platform.confluent.io/managed-cert-add-sans="sr.confluent.demo.com"
```

```text
eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI2ejlLZEc1Z240d182RHdkUWZQbkZzUGJqVVk2U2o5NW52T0JNR0Z0RlBrIn0.eyJleHAiOjE3Mzk0NDU0ODgsImlhdCI6MTczOTQ0NTE4OCwianRpIjoiMDU0NzViYWYtOTZkOS00MTE5LWI4MjctNjZlNzY3ZDk5MTkxIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDgwL3JlYWxtcy9zc29fdGVzdCIsInN1YiI6IjFiMTdkM2VjLWYxNTktNDk3ZS1hMjU4LTU2MjkwYmUwZWRhMiIsInR5cCI6IkJlYXJlciIsImF6cCI6ImRmZWRlcmljbyIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiLyoiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImRlZmF1bHQtcm9sZXMtc3NvX3Rlc3QtMSJdfSwicmVzb3VyY2VfYWNjZXNzIjp7ImRmZWRlcmljbyI6eyJyb2xlcyI6WyJ1bWFfcHJvdGVjdGlvbiJdfX0sInNjb3BlIjoicHJvZmlsZSBlbWFpbCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwiY2xpZW50SG9zdCI6IjE3Mi4xOS4wLjEiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJzZXJ2aWNlLWFjY291bnQtZGZlZGVyaWNvIiwiY2xpZW50QWRkcmVzcyI6IjE3Mi4xOS4wLjEiLCJjbGllbnRfaWQiOiJkZmVkZXJpY28ifQ.fmdienI284Df8szqhnxH2CGoYSEHCms7s_dM_JFSq8ktqm8_k6bWaLssOPgaxLXuEZbU690gisbOwewxX_NDXUOZ9k4gyO0kFdmSogtgE8tZVgnwqaId_lwmdcaHyu8OAIui5AXNNT5GpxHbrr2dOKcZXHuDlu5KiP0ZERLcD75qM5yoqanhW22-uw120go63HQtxesiBRLOiE_rUAwHG0EVXLpiEx8N1RduqESDBX-j71NcPbmKAv45YQdZNcO3ZgEcxClsRQE3OoidV7TjCB6iqVApg0fkBGiBvj08dvv0_BhRdAYeIoxt3yP_GUwU7UY08nCj3zwojKXOekyRrA
```
