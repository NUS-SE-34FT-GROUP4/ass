variable "region" {
  type    = string
  default = "ap-southeast-1"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "az_count" {
  description = "Availability zones to span; ALB and RDS Multi-AZ both need at least two"
  type        = number
  default     = 2
}

variable "single_nat_gateway" {
  description = "One NAT gateway shared by all private subnets (cheaper) instead of one per AZ (survives an AZ outage)"
  type        = bool
  default     = true
}

variable "enable_amazon_mq" {
  description = "Create the Amazon MQ (RabbitMQ) broker. Null means only in the production workspace, since the broker is billed by the hour"
  type        = bool
  default     = null
}

variable "mq_instance_type" {
  description = "Smallest RabbitMQ broker size offered in ap-southeast-1 (about USD 0.17/hour single-instance)"
  type        = string
  default     = "mq.m7g.medium"
}
