#!/bin/bash
# download_and_convert.sh
# Script to download and convert monitoring dashboard files

set -euo pipefail

mkdir -p ./downloaded
mkdir -p ./temp
mkdir -p ./cfk_ready

BASE_URL="https://raw.githubusercontent.com/confluentinc/jmx-monitoring-stacks/refs/heads/main/jmxexporter-prometheus-grafana/assets/grafana/provisioning/dashboards-old-exporter"
DASHBOARD_FILES=(    
    "confluent-platform-kraft.json"
    "kraft.json"    
    "kafka-cluster-kraft.json"
    "schema-registry-cluster.json"
    "kafka-connect-cluster.json"
    "ksqldb-cluster.json"
    "kafka-topics.json"
    "kafka-producer.json"
    "kafka-consumer.json"    
    "kafka-quotas.json"
    "cluster-linking.json"
)
# DASHBOARD_FILES=(    
#     "kafka-connect-cluster.json"
# )

delete_configmaps() {
  # Delete configmaps if they exist
  echo "Deleting configmaps if they exist..."
  for filename in "${DASHBOARD_FILES[@]}"; do
    if kubectl get configmap "$filename" --namespace monitoring &>/dev/null; then
      echo "Deleting configmap: $filename..."
      kubectl delete configmap "$filename" --namespace monitoring
    else
      echo "Configmap $filename does not exist."
    fi
  done
}

download_dashboards() {
  for filename in "${DASHBOARD_FILES[@]}"; do
    echo "Downloading $filename..."
    curl -sSf -o ./downloaded/"$filename" "$BASE_URL/$filename"
  done
}

apply_common_changes() {
  local uid_value="${1:-\$\{DS_PROMETHEUS\}}"
  echo "Applying Dashboards common changes..."
  echo "using prometheus datasource uid value: $uid_value"
  for filename in "${DASHBOARD_FILES[@]}"; do
    echo "First-pass for: $filename..."
    sed "s/\"uid\": \"\${Prometheus}\"/\"uid\": \"$uid_value\"/g;
         s/Environment/Namespace/g;
         s/env/namespace/g;
         s/job=/app=/g;
         s/Instance/Pod/g;
         s/instance/pod/g;
         s/kafka-controller/kraftcontroller/g;
         s/kafka-broker/kafka/g;
         s/schema-registry/schemaregistry/g;
         s/kafka-connect/connect/g" \
         ./downloaded/"$filename" > ./temp/"$filename"
  done
}

remove_pod_from_kafka_controller_metrics() {
  # Hack to remove the pod label from the kafka_controller metrics
  for filename in "${DASHBOARD_FILES[@]}"; do
    echo "Cleaning KRaft metrics for: $filename..."
    trim_json_at_last_brace ./temp/"$filename"
    jq '(.. | objects | select(has("expr")) | .expr) |=
      if test("kafka_controller_") then
        sub(",pod=~\\\"\\$pod\\\""; "") | sub("app=\\\"kafka\\\""; "app=\"kraftcontroller\"")
      else .
      end
    ' ./temp/"$filename" > ./temp/"$filename.tmp" && cp ./temp/"$filename.tmp" ./temp/"$filename"
  done
}

trim_json_at_last_brace() {
  # dashboards like `kafka-cluser-kraft.json` have a trailing symbols at the end of the last line and jq cannot parse the document
  local file="$1"
  # Find the last line number with } or ]
  local last_line
  last_line=$(awk '/[}\]]/ {line=NR} END {print line}' "$file")
  if [[ -n "$last_line" ]]; then
    # Get all lines up to the last one with a closing brace/bracket
    awk -v n="$last_line" 'NR < n { print; next }
      NR == n {
        # Print up to and including the last } or ] on this line
        match($0, /.*[}\]]/)
        if (RSTART > 0) {
          print substr($0, 1, RSTART + RLENGTH - 1)
        }
      }' "$file" > "${file}.trimmed" && mv "${file}.trimmed" "$file"
    echo "Trimmed $file at last valid JSON brace/bracket (line $last_line)."
  else
    echo "No valid JSON brace/bracket found in $file. No changes made."
  fi
}

# Dashboard specific changes
apply_dashboard_specific_changes() {
  echo "Applying Dashboard specific changes..."  
  if [[ -f ./temp/confluent-platform-kraft.json ]]; then
    echo "Applying changes to: confluent-platform-kraft.json"
    sed 's/label_values(namespace)/label_values(kafka_server_kafkaserver_brokerstate, namespace)/g' ./temp/confluent-platform-kraft.json > ./temp/ready-confluent-platform-kraft.json  
  fi
  if [[ -f ./temp/kraft.json ]]; then
    echo "Applying changes to: kraft.json"
    cp ./temp/kraft.json ./temp/ready-kraft.json
  fi
  if [[ -f ./temp/kafka-cluster-kraft.json ]]; then
    echo "Applying changes to: kafka-cluster-kraft.json"
    sed 's/label_values(namespace)/label_values(kafka_server_kafkaserver_brokerstate, namespace)/g;s/clientid/clientId/g' ./temp/kafka-cluster-kraft.json > ./temp/ready-kafka-cluster-kraft.json
  fi
  if [[ -f ./temp/schema-registry-cluster.json ]]; then
    echo "Applying changes to: schema-registry-cluster.json"
    # sed 's/label_values(namespace)/label_values(kafka_schema_registry_registered_count, namespace)/g;s/instance/pod/g;s/schema-registry/schemaregistry/g'
    sed 's/label_values(namespace)/label_values(kafka_schema_registry_registered_count, namespace)/g' ./temp/schema-registry-cluster.json > ./temp/ready-schema-registry-cluster.json
  fi
  if [[ -f ./temp/kafka-connect-cluster.json ]]; then
    echo "Applying changes to: kafka-connect-cluster.json"    
    sed 's/label_values(namespace)/label_values(kafka_connect_app_info, namespace)/g;s/kafka_connect_cluster_id/app/g;s/app=\\\"connect\\\"/app=~\\\"\$app\\\"/g' ./temp/kafka-connect-cluster.json > ./temp/ready-kafka-connect-cluster.json
  fi
  if [[ -f ./temp/ksqldb-cluster.json ]]; then
    echo "Applying changes to: ksqldb-cluster.json"
    cp ./temp/ksqldb-cluster.json ./temp/ready-ksqldb-cluster.json
  fi
  if [[ -f ./temp/kafka-topics.json ]]; then
    echo "Applying changes to: kafka-topics.json"
    sed 's/label_values(namespace)/label_values(kafka_log_log_size, namespace)/g' ./temp/kafka-topics.json > ./temp/ready-kafka-topics.json
  fi
  if [[ -f ./temp/kafka-producer.json ]]; then
    echo "Applying changes to: kafka-producer.json"
    sed 's/label_values(namespace)/label_values(kafka_producer_app, namespace)/g;s/Hostname/Pod/g;s/hostname/pod/g' ./temp/kafka-producer.json > ./temp/ready-kafka-producer.json
  fi
  if [[ -f ./temp/kafka-consumer.json ]]; then
    echo "Applying changes to: kafka-consumer.json"
    sed 's/label_values(namespace)/label_values(kafka_consumer_app, namespace)/g;s/Hostname/Pod/g;s/hostname/pod/g' ./temp/kafka-consumer.json > ./temp/ready-kafka-consumer.json
  fi
  if [[ -f ./temp/kafka-quotas.json ]]; then
    echo "Applying changes to: kafka-quotas.json"
    sed 's/label_values(namespace)/label_values(kafka_server_kafkaserver_brokerstate, namespace)/g' ./temp/kafka-quotas.json > ./temp/ready-kafka-quotas.json
  fi
  if [[ -f ./temp/kafka-quotas.json ]]; then
    echo "Applying changes to: cluster-linking.json"
    sed 's/label_values(namespace)/label_values(kafka_log_log_size, namespace)/g' ./temp/cluster-linking.json > ./temp/ready-cluster-linking.json
  fi
}

move_ready_dashboards() {
  # Move dashboards to cfk directory
  echo "Moving dashboards to cfk directory..."
  for filename in "${DASHBOARD_FILES[@]}"; do
    if [[ -f ./temp/ready-"$filename" ]]; then
      mv ./temp/ready-"$filename" ./cfk_ready/"$filename"
    else
      echo "File ./temp/ready-$filename does not exist, skipping."
    fi
  done
}

clean_temp() {
  # Clean temp directory
  rm -rf ./temp
}

# Create configmaps for each dashboard
create_configmaps() {
  echo "Creating configmaps for each dashboard..."
  for filename in "${DASHBOARD_FILES[@]}"; do
    echo "Configmaps for: $filename..."
    kubectl create configmap "$filename" --namespace monitoring --from-file=./cfk_ready/"$filename"
  done
}

### Label the configmap for loading
label_configmaps() {
  echo "Labeling configmaps for loading..."
  for filename in "${DASHBOARD_FILES[@]}"; do
    echo "Labeling configmap: $filename..."
    kubectl label configmap "$filename" grafana_dashboard="true" --namespace monitoring
  done
}

wait_a_bit() {
  local seconds="${1:-5}"
  local message="${2:-Waiting $seconds seconds...}"
  echo "$message"
  sleep "$seconds"
}

main() {
  delete_configmaps
  download_dashboards
  # if Prometheus datasource uid is not set, replaces with default placeholder value '${DS_PROMETHEUS}'
  apply_common_changes "PBFA97CFB590B2093"
  remove_pod_from_kafka_controller_metrics
  apply_dashboard_specific_changes
  move_ready_dashboards
  clean_temp
  wait_a_bit 10 "Waiting before creating and labeling configmaps..."
  create_configmaps
  label_configmaps
}

main
