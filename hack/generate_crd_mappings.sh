#!/usr/bin/env bash

LOCAL_MANIFEST_FILE=/tmp/crds/selected-crd.yaml

mkdir -p /tmp/crds
kubectl get crds utests.kro.run -oyaml > $LOCAL_MANIFEST_FILE

mkdir -p /tmp/java && cd /tmp/java
docker run \
  --rm \
  -v "$LOCAL_MANIFEST_FILE":"$LOCAL_MANIFEST_FILE" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$(pwd)":"$(pwd)" \
  -ti \
  --network host \
  ghcr.io/kubernetes-client/java/crd-model-gen:v1.0.6 /generate.sh -u $LOCAL_MANIFEST_FILE -n run.kro -p run.kro -o "$(pwd)"

cp -r /tmp/java/src/* ../src