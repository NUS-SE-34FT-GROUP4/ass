#!/usr/bin/env bash
# Run a SQL file against the environment's RDS instance as the master user,
# from a one-off ECS task inside the VPC (RDS has no public endpoint).
#
#   infra/scripts/run-sql.sh staging init.sql
#   infra/scripts/run-sql.sh staging db/grants.sql     # WP8 per-service users
#
# The file is gzipped and base64-encoded into the task override, which ECS
# limits to 8 KB; init.sql is about 5 KB after encoding.
set -euo pipefail

workspace=${1:?usage: run-sql.sh <staging|production> <file.sql>}
sql_file=${2:?usage: run-sql.sh <staging|production> <file.sql>}
tf_dir="$(cd "$(dirname "$0")/../terraform/env" && pwd)"

current=$(terraform -chdir="$tf_dir" workspace show)
if [[ $current != "$workspace" ]]; then
  echo "Terraform workspace is $current, not $workspace" >&2
  exit 1
fi

out() { terraform -chdir="$tf_dir" output -raw "$1"; }
cluster=$(out ecs_cluster)
family=$(out db_admin_task_family)
sg=$(out service_security_group_id)
subnet=$(terraform -chdir="$tf_dir" output -json private_subnet_ids | python3 -c 'import json,sys; print(json.load(sys.stdin)[0])')

payload=$(gzip -9c "$sql_file" | base64 | tr -d '\n')
if (( ${#payload} > 7500 )); then
  echo "$sql_file is ${#payload} bytes encoded; split it below 7500" >&2
  exit 1
fi

overrides=$(python3 -c 'import json,sys; print(json.dumps({"containerOverrides":[{"name":"db-admin","environment":[{"name":"SQL_GZ_B64","value":sys.argv[1]}]}]}))' "$payload")

task=$(aws ecs run-task \
  --cluster "$cluster" --task-definition "$family" --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$subnet],securityGroups=[$sg],assignPublicIp=DISABLED}" \
  --overrides "$overrides" --query 'tasks[0].taskArn' --output text)
echo "Started $task"

aws ecs wait tasks-stopped --cluster "$cluster" --tasks "$task"
code=$(aws ecs describe-tasks --cluster "$cluster" --tasks "$task" \
  --query 'tasks[0].containers[0].exitCode' --output text)

aws logs tail "/ecs/c2csectrade-$workspace/db-admin" --since 15m --format short || true
echo "Exit code: $code"
[[ $code == 0 ]]
