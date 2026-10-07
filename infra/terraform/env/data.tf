# Data tier: RDS for MySQL, ElastiCache for Redis, OpenSearch. All in the
# private subnets, all encrypted at rest, reachable only from the services.

# ---------------------------------------------------------------- RDS MySQL

resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_parameter_group" "mysql" {
  name   = "${local.name}-mysql8"
  family = "mysql8.0"

  # init.sql and the application expect utf8mb4 throughout (compose.yaml sets the same).
  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }
  parameter {
    name  = "collation_server"
    value = "utf8mb4_unicode_ci"
  }
}

resource "aws_db_instance" "mysql" {
  identifier     = local.name
  engine         = "mysql"
  engine_version = "8.0"
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_allocated_storage * 2
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = "admin"
  # RDS generates the master password and keeps it in Secrets Manager.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.data["rds"].id]
  parameter_group_name   = aws_db_parameter_group.mysql.name
  publicly_accessible    = false

  # Production: standby in the second AZ and a week of automated backups.
  # Staging: single-AZ, no backups, nothing kept on destroy.
  multi_az                  = local.is_prod
  backup_retention_period   = local.is_prod ? 7 : 0
  deletion_protection       = local.is_prod
  skip_final_snapshot       = !local.is_prod
  final_snapshot_identifier = local.is_prod ? "${local.name}-final" : null

  auto_minor_version_upgrade = true
  apply_immediately          = !local.is_prod
}

# One database user per service (proposal: Core and Chat each connect with their
# own user). Terraform only generates the credentials; ZHOU YUXIN's grant script
# (WP8) creates the users with them. Until it has run, set db_use_master_user.
locals {
  db_services = toset(["core", "chat"])
}

resource "random_password" "db" {
  for_each = local.db_services
  length   = 32
  special  = false
}

resource "aws_secretsmanager_secret" "db" {
  for_each                = local.db_services
  name                    = "${local.name}/db/${each.key}"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "db" {
  for_each  = local.db_services
  secret_id = aws_secretsmanager_secret.db[each.key].id
  secret_string = jsonencode({
    username = "${each.key}_svc"
    password = random_password.db[each.key].result
  })
}

# ---------------------------------------------------------------- ElastiCache Redis

resource "aws_elasticache_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = local.name
  description          = "Cache, browsing history, recommendations and distributed locks"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.redis_node_type
  port                 = 6379

  # One node: the cache can be rebuilt, and locks only need a single primary.
  num_cache_clusters         = 1
  automatic_failover_enabled = false

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.data["redis"].id]

  at_rest_encryption_enabled = true
  # TLS in transit; the services set spring.data.redis.ssl.enabled=true.
  transit_encryption_enabled = true

  apply_immediately = !local.is_prod
}

# ---------------------------------------------------------------- OpenSearch

locals {
  opensearch_nodes = coalesce(var.opensearch_instance_count, local.is_prod ? 2 : 1)
}

resource "aws_opensearch_domain" "search" {
  # Domain names are at most 28 characters.
  domain_name    = substr("c2c-${terraform.workspace}", 0, 28)
  engine_version = "OpenSearch_2.17"

  cluster_config {
    instance_type          = var.opensearch_instance_type
    instance_count         = local.opensearch_nodes
    zone_awareness_enabled = local.opensearch_nodes > 1
    dynamic "zone_awareness_config" {
      for_each = local.opensearch_nodes > 1 ? [1] : []
      content {
        availability_zone_count = 2
      }
    }
  }

  ebs_options {
    ebs_enabled = true
    volume_type = "gp3"
    volume_size = var.opensearch_volume_gb
  }

  vpc_options {
    subnet_ids         = slice(aws_subnet.private[*].id, 0, local.opensearch_nodes > 1 ? 2 : 1)
    security_group_ids = [aws_security_group.data["opensearch"].id]
  }

  encrypt_at_rest {
    enabled = true
  }

  node_to_node_encryption {
    enabled = true
  }

  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-2019-07"
  }
}

# Inside the VPC the security group is the gate: only the services reach port
# 443, and requests need no SigV4 signing (the Elasticsearch client sends none).
resource "aws_opensearch_domain_policy" "search" {
  domain_name = aws_opensearch_domain.search.domain_name
  access_policies = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { AWS = "*" }
      Action    = "es:ESHttp*"
      Resource  = "${aws_opensearch_domain.search.arn}/*"
    }]
  })
}
