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

output "rabbitmq_endpoint" {
  description = "amqps:// endpoint of the Amazon MQ broker (production only)"
  value       = local.enable_mq ? aws_mq_broker.rabbitmq[0].instances[0].endpoints[0] : null
}

output "rabbitmq_secret_arn" {
  value = local.enable_mq ? aws_secretsmanager_secret.mq[0].arn : null
}
