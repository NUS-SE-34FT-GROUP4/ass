#!/usr/bin/env bash
# Deploys one image tag to ECS services and waits until each rollout is done.
#
# Usage: scripts/ecs-deploy.sh CLUSTER TAG SERVICE...
#   ECR_REGISTRY      registry host, e.g. 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com
#   DEPLOY_TIMEOUT    seconds to wait for all rollouts (default 900)
#   POLL_SECONDS      seconds between status checks (default 15)
#
# For each service: take the task definition it runs now, register a new
# revision with the image $ECR_REGISTRY/c2csectrade/<service>:TAG and point
# the service at it. Then wait for every rollout. A rollout counts as done
# only when ECS reports it COMPLETED; if the deployment circuit breaker rolls
# it back (tasks never pass the load balancer health check), this fails.
# Needs bash 4.4 or later (the GitHub runners have 5), the AWS CLI and jq.

set -euo pipefail

if [ "$#" -lt 3 ] || [ -z "${ECR_REGISTRY:-}" ]; then
    echo "usage: ECR_REGISTRY=... $0 CLUSTER TAG SERVICE..." >&2
    exit 2
fi

CLUSTER="$1"
TAG="$2"
shift 2
SERVICES=("$@")
TIMEOUT="${DEPLOY_TIMEOUT:-900}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

declare -A NEW_REVISION

for service in "${SERVICES[@]}"; do
    image="$ECR_REGISTRY/c2csectrade/$service:$TAG"
    current="$(aws ecs describe-services --cluster "$CLUSTER" --services "$service" \
        --query 'services[0].taskDefinition' --output text)"

    # Read-only fields of a registered revision cannot be sent back
    aws ecs describe-task-definition --task-definition "$current" --query taskDefinition --output json \
        | jq --arg name "$service" --arg image "$image" '
            .containerDefinitions |= map(if .name == $name then .image = $image else . end)
            | del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .compatibilities,
                  .registeredAt, .registeredBy, .deregisteredAt)' \
        > "$WORK/$service.json"

    revision="$(aws ecs register-task-definition --cli-input-json "file://$WORK/$service.json" \
        --query taskDefinition.taskDefinitionArn --output text)"
    aws ecs update-service --cluster "$CLUSTER" --service "$service" --task-definition "$revision" \
        --query service.serviceName --output text > /dev/null
    NEW_REVISION[$service]="$revision"
    echo "$service: ${current##*/} -> ${revision##*/} ($image)"
done

deadline=$((SECONDS + TIMEOUT))
pending=("${SERVICES[@]}")
failed=0
while [ "${#pending[@]}" -gt 0 ]; do
    still=()
    for service in "${pending[@]}"; do
        revision="${NEW_REVISION[$service]}"
        state="$(aws ecs describe-services --cluster "$CLUSTER" --services "$service" \
            --query "services[0].deployments[?taskDefinition=='$revision'] | [0].[rolloutState, rolloutStateReason]" \
            --output text)"
        case "$state" in
            COMPLETED*)
                echo "$service: rollout completed" ;;
            FAILED*)
                echo "$service: rollout failed, rolled back by the circuit breaker: ${state#FAILED$'\t'}" >&2
                failed=1 ;;
            None* | "")
                echo "$service: deployment of ${revision##*/} is gone (replaced or rolled back)" >&2
                failed=1 ;;
            *)
                still+=("$service") ;;
        esac
    done
    pending=("${still[@]}")
    [ "${#pending[@]}" -eq 0 ] && break
    if [ "$SECONDS" -ge "$deadline" ]; then
        echo "Timed out after ${TIMEOUT}s waiting for: ${pending[*]}" >&2
        exit 1
    fi
    sleep "${POLL_SECONDS:-15}"
done

exit "$failed"
