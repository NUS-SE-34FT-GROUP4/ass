# RabbitMQ carries the domain events (Search indexing, Chat cross-instance
# delivery and notifications), so every environment has one. Production uses
# Amazon MQ. Staging runs rabbitmq:3.12-management on ECS to stay within the
# credit budget; its messages do not survive a task restart.

locals {
  rabbitmq_mode = coalesce(var.rabbitmq_mode, local.is_prod ? "amazon_mq" : "container")
  mq_managed    = local.rabbitmq_mode == "amazon_mq"
  mq_user       = "c2csectrade"

  # What the services are given, whichever way the broker is provided.
  mq_host = local.mq_managed ? (
    trimsuffix(trimprefix(aws_mq_broker.rabbitmq[0].instances[0].endpoints[0], "amqps://"), ":5671")
  ) : "rabbitmq.${aws_service_discovery_private_dns_namespace.internal.name}"
  mq_port = local.mq_managed ? 5671 : 5672
  mq_ssl  = local.mq_managed
}

resource "terraform_data" "rabbitmq_mode_guard" {
  input = local.rabbitmq_mode
  lifecycle {
    precondition {
      condition     = !(local.is_prod && local.rabbitmq_mode == "container")
      error_message = "Production always uses Amazon MQ; the RabbitMQ container is a staging-only fallback."
    }
  }
}

# Amazon MQ rejects commas, colons and equals signs, so no special characters.
resource "random_password" "mq" {
  length  = 32
  special = false
}

# The services read the broker credentials from here, not from the repository.
resource "aws_secretsmanager_secret" "mq" {
  name                    = "${local.name}/rabbitmq"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "mq" {
  secret_id = aws_secretsmanager_secret.mq.id
  secret_string = jsonencode({
    username = local.mq_user
    password = random_password.mq.result
  })
}

# ---------------------------------------------------------------- Amazon MQ

resource "aws_security_group" "mq" {
  name        = "${local.name}-mq"
  description = "RabbitMQ from the ECS services only"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${local.name}-mq" }
}

resource "aws_vpc_security_group_ingress_rule" "mq_from_services" {
  for_each                     = toset(local.mq_managed ? ["5671"] : ["5672"])
  security_group_id            = aws_security_group.mq.id
  referenced_security_group_id = aws_security_group.services.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.key)
  to_port                      = tonumber(each.key)
  description                  = local.mq_managed ? "AMQPS" : "AMQP"
}

resource "aws_vpc_security_group_egress_rule" "mq_all" {
  security_group_id = aws_security_group.mq.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_mq_broker" "rabbitmq" {
  count                      = local.mq_managed ? 1 : 0
  broker_name                = "${local.name}-rabbitmq"
  engine_type                = "RabbitMQ"
  engine_version             = "3.13"
  auto_minor_version_upgrade = true
  host_instance_type         = var.mq_instance_type
  deployment_mode            = "SINGLE_INSTANCE"
  publicly_accessible        = false
  subnet_ids                 = [aws_subnet.private[0].id]
  security_groups            = [aws_security_group.mq.id]

  user {
    username = local.mq_user
    password = random_password.mq.result
  }

  logs {
    general = true
  }
}

# ---------------------------------------------------------------- container (staging fallback)

resource "aws_cloudwatch_log_group" "rabbitmq" {
  count             = local.mq_managed ? 0 : 1
  name              = "/ecs/${local.name}/rabbitmq"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_task_definition" "rabbitmq" {
  count                    = local.mq_managed ? 0 : 1
  family                   = "${local.name}-rabbitmq"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.task_execution.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "rabbitmq"
    image        = "rabbitmq:3.12-management-alpine"
    essential    = true
    portMappings = [{ containerPort = 5672, protocol = "tcp" }]
    secrets = [
      { name = "RABBITMQ_DEFAULT_USER", valueFrom = "${aws_secretsmanager_secret.mq.arn}:username::" },
      { name = "RABBITMQ_DEFAULT_PASS", valueFrom = "${aws_secretsmanager_secret.mq.arn}:password::" },
    ]
    # Only probe the port: rabbitmq-diagnostics runs as root and creates a
    # root-owned .erlang.cookie that the server then cannot read.
    healthCheck = {
      command     = ["CMD-SHELL", "nc -z 127.0.0.1 5672 || exit 1"]
      interval    = 15
      timeout     = 10
      retries     = 5
      startPeriod = 30
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.rabbitmq[0].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "rabbitmq"
      }
    }
  }])
}

resource "aws_service_discovery_service" "rabbitmq" {
  count = local.mq_managed ? 0 : 1
  name  = "rabbitmq"
  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.internal.id
    dns_records {
      type = "A"
      ttl  = 10
    }
  }
}

resource "aws_ecs_service" "rabbitmq" {
  count           = local.mq_managed ? 0 : 1
  name            = "rabbitmq"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.rabbitmq[0].arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = [aws_subnet.private[0].id]
    security_groups = [aws_security_group.mq.id]
  }

  service_registries {
    registry_arn = aws_service_discovery_service.rabbitmq[0].arn
  }
}
