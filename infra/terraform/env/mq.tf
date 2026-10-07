# Amazon MQ for RabbitMQ: the managed replacement for the RabbitMQ container.
# Created only in production by default. Staging leaves it out to save cost.

locals {
  enable_mq = coalesce(var.enable_amazon_mq, terraform.workspace == "production")
}

resource "aws_security_group" "mq" {
  count       = local.enable_mq ? 1 : 0
  name        = "${local.name}-mq"
  description = "AMQPS from inside the VPC only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "AMQPS"
    from_port   = 5671
    to_port     = 5671
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name}-mq" }
}

# Amazon MQ rejects commas, colons and equals signs, so no special characters.
resource "random_password" "mq" {
  count   = local.enable_mq ? 1 : 0
  length  = 32
  special = false
}

resource "aws_mq_broker" "rabbitmq" {
  count                      = local.enable_mq ? 1 : 0
  broker_name                = "${local.name}-rabbitmq"
  engine_type                = "RabbitMQ"
  engine_version             = "3.13"
  auto_minor_version_upgrade = true
  host_instance_type         = var.mq_instance_type
  deployment_mode            = "SINGLE_INSTANCE"
  publicly_accessible        = false
  subnet_ids                 = [aws_subnet.private[0].id]
  security_groups            = [aws_security_group.mq[0].id]

  user {
    username = "c2csectrade"
    password = random_password.mq[0].result
  }

  logs {
    general = true
  }
}

# The application reads the broker credentials from here, not from the repo.
resource "aws_secretsmanager_secret" "mq" {
  count                   = local.enable_mq ? 1 : 0
  name                    = "${local.name}/rabbitmq"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "mq" {
  count     = local.enable_mq ? 1 : 0
  secret_id = aws_secretsmanager_secret.mq[0].id
  secret_string = jsonencode({
    username = "c2csectrade"
    password = random_password.mq[0].result
    endpoint = aws_mq_broker.rabbitmq[0].instances[0].endpoints[0]
  })
}
