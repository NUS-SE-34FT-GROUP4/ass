# Service tier: one ECS Fargate service per microservice behind the ALB.
# Terraform registers the first task definition of each; CI registers new
# revisions with new image tags, so later deploys do not show up as drift.

resource "aws_ecs_cluster" "main" {
  name = local.name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]
  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }
}

# Private DNS (<name>.c2csectrade-<workspace>.internal) for anything the services
# reach by name inside the VPC, such as the staging RabbitMQ container.
resource "aws_service_discovery_private_dns_namespace" "internal" {
  name = "${local.name}.internal"
  vpc  = aws_vpc.main.id
}

data "aws_ecr_repository" "service" {
  for_each = var.services
  name     = "c2csectrade/${each.key}"
}

resource "aws_cloudwatch_log_group" "service" {
  for_each          = var.services
  name              = "/ecs/${local.name}/${each.key}"
  retention_in_days = var.log_retention_days
}

# ---------------------------------------------------------------- secrets

# HMAC key every service uses to validate JWTs itself (no call to Core).
resource "random_bytes" "jwt" {
  length = 64
}

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${local.name}/jwt"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_bytes.jwt.base64
}

# ---------------------------------------------------------------- IAM

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = ["arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:*"]
    }
  }
}

# Used by ECS itself: pull images, write logs, inject secrets.
resource "aws_iam_role" "task_execution" {
  name               = "${local.name}-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

locals {
  app_secret_arns = concat(
    [aws_secretsmanager_secret.jwt.arn, aws_secretsmanager_secret.mq.arn, aws_db_instance.mysql.master_user_secret[0].secret_arn],
    [for s in aws_secretsmanager_secret.db : s.arn],
  )
}

resource "aws_iam_role_policy" "task_execution_secrets" {
  name = "read-app-secrets"
  role = aws_iam_role.task_execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "secretsmanager:GetSecretValue"
      Resource = local.app_secret_arns
    }]
  })
}

# Used by the application code. One role per service, so only Core can write media.
resource "aws_iam_role" "task" {
  for_each           = var.services
  name               = "${local.name}-${each.key}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy" "core_media" {
  name = "media-bucket"
  role = aws_iam_role.task["core"].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${aws_s3_bucket.media.arn}/*"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketLocation"]
        Resource = aws_s3_bucket.media.arn
      },
    ]
  })
}

# ECS Exec (aws ecs execute-command) for debugging a running task.
resource "aws_iam_role_policy" "task_exec" {
  for_each = var.services
  name     = "ecs-exec"
  role     = aws_iam_role.task[each.key].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "ssmmessages:CreateControlChannel", "ssmmessages:CreateDataChannel",
        "ssmmessages:OpenControlChannel", "ssmmessages:OpenDataChannel",
      ]
      Resource = "*"
    }]
  })
}

# ---------------------------------------------------------------- task definitions

locals {
  # Search keeps Core's database user only until it is extracted and reads
  # nothing but OpenSearch; then drop the datasource from its task definition.
  db_secret_for = { core = "core", search = "core", chat = "chat" }

  db_url = join("", [
    "jdbc:mysql://${aws_db_instance.mysql.address}:${aws_db_instance.mysql.port}/${var.db_name}",
    "?sslMode=REQUIRED&serverTimezone=Asia/Singapore&characterEncoding=UTF-8",
    "&connectionCollation=utf8mb4_unicode_ci&useServerPrepStmts=true&cachePrepStmts=true&rewriteBatchedStatements=true",
  ])

  # Spring Boot relaxed binding: SPRING_DATA_REDIS_HOST overrides spring.data.redis.host.
  common_environment = {
    SPRING_PROFILES_ACTIVE        = "docker"
    SPRING_DATASOURCE_URL         = local.db_url
    SPRING_DATA_REDIS_HOST        = aws_elasticache_replication_group.redis.primary_endpoint_address
    SPRING_DATA_REDIS_PORT        = "6379"
    SPRING_DATA_REDIS_SSL_ENABLED = "true"
    SPRING_RABBITMQ_HOST          = local.mq_host
    SPRING_RABBITMQ_PORT          = tostring(local.mq_port)
    SPRING_RABBITMQ_SSL_ENABLED   = tostring(local.mq_ssl)
    SPRING_ELASTICSEARCH_URIS     = "https://${aws_opensearch_domain.search.endpoint}:443"
    # See OpenSearchCompatibilityConfig: the ES 8 client needs this to talk to OpenSearch.
    APP_SEARCH_OPENSEARCHCOMPATIBILITY = "true"
    MINIO_ENDPOINT                     = "https://s3.${var.region}.amazonaws.com"
    MINIO_BUCKETNAME                   = aws_s3_bucket.media.id
    MINIO_REGION                       = var.region
    # Empty keys override the local defaults, so MinioConfig uses the task role.
    MINIO_ACCESSKEY       = ""
    MINIO_SECRETKEY       = ""
    MEDIA_PUBLIC_BASE_URL = "https://${aws_cloudfront_distribution.main.domain_name}/media"
    AWS_REGION            = var.region
    APP_ENVIRONMENT       = terraform.workspace
  }

  db_user_secret = {
    for svc in keys(var.services) : svc => (
      var.db_use_master_user
      ? aws_db_instance.mysql.master_user_secret[0].secret_arn
      : aws_secretsmanager_secret.db[local.db_secret_for[svc]].arn
    )
  }
}

resource "aws_ecs_task_definition" "service" {
  for_each                 = var.services
  family                   = "${local.name}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task[each.key].arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = each.key
    image        = "${data.aws_ecr_repository.service[each.key].repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ containerPort = var.container_port, protocol = "tcp" }]

    environment = [for k, v in merge(local.common_environment, { SERVICE_NAME = each.key }) : { name = k, value = v }]
    secrets = [
      { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt.arn },
      { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${local.db_user_secret[each.key]}:username::" },
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${local.db_user_secret[each.key]}:password::" },
      { name = "SPRING_RABBITMQ_USERNAME", valueFrom = "${aws_secretsmanager_secret.mq.arn}:username::" },
      { name = "SPRING_RABBITMQ_PASSWORD", valueFrom = "${aws_secretsmanager_secret.mq.arn}:password::" },
    ]

    readonlyRootFilesystem = false
    linuxParameters        = { initProcessEnabled = true }

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.service[each.key].name
        awslogs-region        = var.region
        awslogs-stream-prefix = each.key
      }
    }
  }])

  depends_on = [aws_secretsmanager_secret_version.jwt, aws_secretsmanager_secret_version.mq]
}

# ---------------------------------------------------------------- services

resource "aws_ecs_service" "service" {
  for_each        = var.services
  name            = each.key
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.service[each.key].arn
  desired_count   = each.value.desired_count
  launch_type     = "FARGATE"

  # Spring Boot needs a while before /actuator/health answers.
  health_check_grace_period_seconds = 120
  enable_execute_command            = true

  # Staging runs one task per service in one AZ; production spreads over both.
  network_configuration {
    subnets          = local.is_prod ? aws_subnet.private[*].id : [aws_subnet.private[0].id]
    security_groups  = [aws_security_group.services.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.service[each.key].arn
    container_name   = each.key
    container_port   = var.container_port
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  # A release whose tasks never become healthy is rolled back automatically.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  lifecycle {
    # CI owns the task definition revision; auto-scaling owns the task count.
    ignore_changes = [task_definition, desired_count]
  }

  depends_on = [aws_lb_listener.http]
}

# ---------------------------------------------------------------- one-off SQL

# Runs SQL against RDS as the master user from inside the VPC, for init.sql and
# the per-service grant script. See infra/scripts/run-sql.sh. The SQL arrives
# gzipped and base64-encoded in SQL_GZ_B64 (task overrides are limited to 8 KB).
resource "aws_cloudwatch_log_group" "db_admin" {
  name              = "/ecs/${local.name}/db-admin"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_task_definition" "db_admin" {
  family                   = "${local.name}-db-admin"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.task_execution.arn

  container_definitions = jsonencode([{
    name       = "db-admin"
    image      = "mysql:8.0"
    essential  = true
    entryPoint = ["sh", "-c"]
    command    = ["echo \"$SQL_GZ_B64\" | base64 -d | gunzip | mysql --ssl-mode=REQUIRED -h \"$DB_HOST\" -u \"$DB_USER\" -p\"$DB_PASSWORD\" -v"]
    environment = [
      { name = "DB_HOST", value = aws_db_instance.mysql.address },
      { name = "SQL_GZ_B64", value = "" },
    ]
    secrets = [
      { name = "DB_USER", valueFrom = "${aws_db_instance.mysql.master_user_secret[0].secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${aws_db_instance.mysql.master_user_secret[0].secret_arn}:password::" },
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.db_admin.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "db-admin"
      }
    }
  }])
}
