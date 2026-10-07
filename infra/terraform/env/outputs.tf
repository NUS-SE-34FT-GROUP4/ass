output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "availability_zones" {
  value = local.azs
}

output "app_url" {
  description = "Public entry point: frontend, /api and /ws"
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.main.id
}

output "alb_dns_name" {
  description = "Accepts traffic from CloudFront only"
  value       = aws_lb.main.dns_name
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "ecs_services" {
  value = { for k, s in aws_ecs_service.service : k => s.name }
}

output "task_definition_families" {
  value = { for k, t in aws_ecs_task_definition.service : k => t.family }
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.id
}

output "media_bucket" {
  value = aws_s3_bucket.media.id
}

output "github_deploy_role_arn" {
  description = "AWS_ROLE_ARN for the GitHub Environment of the same name"
  value       = aws_iam_role.github_deploy.arn
}

output "rds_endpoint" {
  value = aws_db_instance.mysql.address
}

output "rds_master_secret_arn" {
  value = aws_db_instance.mysql.master_user_secret[0].secret_arn
}

output "db_user_secret_arns" {
  value = { for k, s in aws_secretsmanager_secret.db : k => s.arn }
}

output "redis_endpoint" {
  value = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "opensearch_endpoint" {
  value = aws_opensearch_domain.search.endpoint
}

output "rabbitmq_mode" {
  value = local.rabbitmq_mode
}

output "rabbitmq_host" {
  value = local.mq_host
}

output "rabbitmq_secret_arn" {
  value = aws_secretsmanager_secret.mq.arn
}

output "jwt_secret_arn" {
  value = aws_secretsmanager_secret.jwt.arn
}

output "db_admin_task_family" {
  value = aws_ecs_task_definition.db_admin.family
}

output "service_security_group_id" {
  value = aws_security_group.services.id
}
