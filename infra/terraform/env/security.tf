# Security groups. Traffic flows one way down the tiers:
#   CloudFront -> ALB -> ECS services -> RDS / ElastiCache / OpenSearch / RabbitMQ
# Each tier accepts connections only from the security group above it.

# CloudFront's origin-facing addresses: the ALB is not reachable from anywhere else.
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "HTTP from CloudFront only"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${local.name}-alb" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  security_group_id = aws_security_group.alb.id
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_egress_rule" "alb_to_services" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.services.id
  ip_protocol                  = "tcp"
  from_port                    = var.container_port
  to_port                      = var.container_port
}

resource "aws_security_group" "services" {
  name        = "${local.name}-services"
  description = "ECS tasks: traffic from the ALB only"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${local.name}-services" }
}

resource "aws_vpc_security_group_ingress_rule" "services_from_alb" {
  security_group_id            = aws_security_group.services.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = var.container_port
  to_port                      = var.container_port
}

# Outbound: data stores in the VPC, plus ECR, Secrets Manager, S3 and
# CloudWatch over HTTPS through the NAT gateway.
resource "aws_vpc_security_group_egress_rule" "services_all" {
  security_group_id = aws_security_group.services.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# One group per data store, each open on its own port to the services only.
locals {
  data_stores = {
    rds        = { port = 3306, description = "MySQL" }
    redis      = { port = 6379, description = "Redis" }
    opensearch = { port = 443, description = "OpenSearch HTTPS" }
  }
}

resource "aws_security_group" "data" {
  for_each    = local.data_stores
  name        = "${local.name}-${each.key}"
  description = "${each.value.description} from the ECS services only"
  vpc_id      = aws_vpc.main.id
  tags        = { Name = "${local.name}-${each.key}" }
}

resource "aws_vpc_security_group_ingress_rule" "data_from_services" {
  for_each                     = local.data_stores
  security_group_id            = aws_security_group.data[each.key].id
  referenced_security_group_id = aws_security_group.services.id
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
  description                  = each.value.description
}
