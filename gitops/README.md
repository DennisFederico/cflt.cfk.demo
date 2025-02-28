# Gitops using ArgoCD

This is a technical integration example of ArgoCD with CFK, it intentionally falls short on the GitOps side, synching only on the `HEAD` of the repository.

Behind the scenes Argo interacts with the `Kubernetes Control Plane` (similar to using kubectl), when a `CFK CRD` is deployed or it's state changed, the `CFK Operator` is notified via `K8s Informers` and take action during the `reconciliation loop` making any changes required to bring the desired state or even stopping the reconciliation in case of incompatibilities or not-yet supported changes (i.e. increase topic partitions).

**Important**: This demo deploys "Application Resources" as in Topics, Connectors, ConfluentRolebindigs, Schemas, etc ... altough the same approach can be used to deploy "Platform Resources" (Infraestructure) like Kafka Cluster, Connect, SchemaRegistry, etc.

![alt text](image.png)

## Setup ArgoCD

This is [quickstart](https://argo-cd.readthedocs.io/en/stable/getting_started/) approach of setting up ArgoCD without the Argo CLI.

### Install ArgoCD

```shell
# Create a namespace for Argo CD
kubectl create namespace argocd

# Apply the Argo CD installation manifest
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for the pods to be ready
kubectl wait --for=condition=Ready pods --all -n argocd --timeout=300s
```

### Access ArgoCD

Portforward or...

```shell
# Port-forward the Argo CD server
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

...Service (LB) - see [service-argocd.yaml](service-argocd.yaml)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: argocd-server-lb
  namespace: argocd
  labels:
    app.kubernetes.io/component: server
    app.kubernetes.io/name: argocd-server
    app.kubernetes.io/part-of: argocd
spec:
  type: LoadBalancer
  ports:
  - name: http
    port: 80
    targetPort: 8080
  - name: https
    port: 443
    targetPort: 8080
  selector:
    app.kubernetes.io/name: argocd-server
```

```shell
kubectl apply -f gitops/service-argocd.yaml -n argocd
```

***NOTE:*** The default used is `admin` but the password is generated and stored in a secret named `argocd-initial-admin-secret`

```shell
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

### Connect to Github repository

You will need access credentials to the repository, in this case we will use (SSH).

Create credentials and configure the public key on Github side.

```shell
ssh-keygen -t ed25519 -C "your-email@or-any-id.for-key"
```

Create and deploy a secret in the ArgoCD namespace with the proper `labels` for the operator to trigger its configuration - see [cfk-gitops-repo.yaml](repo/cfk-gitops-repo.yaml)

```shell
apiVersion: v1
kind: Secret
metadata:
  name: cfk-gitops-repo
  namespace: argocd
  labels:
    argocd.argoproj.io/secret-type: repository
stringData:
  type: git
  url: git@github.com:ORG_REPO/REPOSITORY.git
  sshPrivateKey: |
    -----BEGIN OPENSSH PRIVATE KEY-----
    ...
    -----END OPENSSH PRIVATE KEY-----
  insecure: "false"
  enableLfs: "false"
```

```shell
kubectl apply -f gitops/github-secret.yaml -n argocd
```

## Project

We will use the ArgoCD `default` project, which is already configured with the hosting k8s (roles and other artifact alrady deployed with the install script), and configured with the Github acccess in the previous step.

### Repository Directory Structure

There are different approaches on how to structure the repository, but a common practice among them is to isolate the descriptors in specific `folder` that will be configured in the ArgoCD application deployment as "root". From there you can add folders by environment, by resource or both. Use [Kustomize](https://kustomize.io/) to separate `base` templates for resources and/or resource per environment, from the actual configuration values (`overlays`).

| Example 1 | Example 2 |
|-----------|-----------|
| ![Structure Example 1](img/folders1.png) | ![Structure Example 2](img/folders2.png) |

Kustomize is already supported by ArgoCD, notice that the choice of `base` and `overlays` folders in the examples above answers the convention when using kustomize, but know that when the `kustomization.yaml` file exists at the location pointed to by repoURL and path, Argo CD will render the manifests using Kustomize.

This example won't use Kustomize... yet!

### Actual Directory and Contents

We will use this same demo repository, but monitor the [current-path/repo/cfk](./repo/cfk) path

### Target Confluent Platform

This demo assumes the deployment of a cluster in the `confluent` namespace using the [platform descriptor](../infra/auth/platform-mtls-oauth-rbac.yaml) that comprise a Kafka cluster with mTLS for internal listener authentication and for REST on the Services like Connect, SchemaRegistry, etc. The target cluster is also RBAC enabled, to empathize the need to deploy `confluentrolebindigs`.

## ArgoCD Application

Refer to [application.yaml](repo/application.yaml) manifest, the content is simple but well worth noticing the `spec.source.repoURL` that is the same as the repository configured before, and `spec.source.path` as the repository folder to "monitor".

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: cfk-gitops
  namespace: argocd
spec:
  project: default
  source:
    repoURL: git@github.com:DennisFederico/cflt.cfk.demo.git
    targetRevision: HEAD
    path: gitops/repo/cfk
    directory:
      recurse: true
  destination:
    server: https://kubernetes.default.svc
    namespace: confluent
  syncPolicy:
    automated:
      prune: true
      selfHeal: false
  sources: []
```

```shell
kubectl apply -f gitops/repo/application.yaml
```

### Resource Ordering

To prescribe the order in which resources are `synched` with ArgoCD, we use [waves](https://argo-cd.readthedocs.io/en/stable/user-guide/sync-waves/). Note the label annotation like:

```yaml
---
apiVersion: platform.confluent.io/v1beta1
kind: KafkaTopic
metadata:
  name: pizza-orders
  namespace: confluent
  annotations:
    argocd.argoproj.io/sync-wave: "2"
...
```

In the above example we state that this resources must be synched in the wave 2 (after waves 0 and 1).

Considering the above, a simple ordering strategy would be:

1. Rolebindings
2. Topics
3. Schemas
4. Connectors

Leaving wave 0 for other non CFK resources like K8s Secrets

### Additional Considerations



### Application Credentials (Connect)

```shell
kubectl create secret generic appuser-secret \
  --from-file=tls.crt=certs/generated/client.pem \
  --from-file=ca.crt=certs/generated/InternalCAcert.pem \
  --from-file=tls.key=certs/generated/client-key.pem
```

