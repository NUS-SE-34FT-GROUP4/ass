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

# ---------------------------------------------------------------- RabbitMQ

variable "rabbitmq_mode" {
  description = <<-EOT
    How RabbitMQ is provided. Search indexing and Chat cross-instance delivery
    both depend on it, so it is never switched off.
      amazon_mq  Amazon MQ broker (default, both workspaces)
      container  rabbitmq:3.12-management task on ECS; cheaper, staging only,
                 messages are lost when the task restarts
  EOT
  type        = string
  default     = "amazon_mq"
  validation {
    condition     = contains(["amazon_mq", "container"], var.rabbitmq_mode)
    error_message = "rabbitmq_mode must be amazon_mq or container."
  }
}

variable "mq_instance_type" {
  description = "Smallest RabbitMQ broker size offered in ap-southeast-1"
  type        = string
  default     = "mq.m7g.medium"
}

# ---------------------------------------------------------------- data tier

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "GiB"
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Schema created on the RDS instance; init.sql uses trade"
  type        = string
  default     = "trade"
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "opensearch_instance_type" {
  type    = string
  default = "t3.small.search"
}

variable "opensearch_instance_count" {
  description = "Data nodes. Null means 1 in staging and 2 (one per AZ) in production"
  type        = number
  default     = null
}

variable "opensearch_volume_gb" {
  type    = number
  default = 10
}

# ---------------------------------------------------------------- services

variable "image_tag" {
  description = "Image tag in the task definitions Terraform registers. CI registers later revisions itself"
  type        = string
  default     = "latest"
}

variable "services" {
  description = <<-EOT
    The three microservices. paths are ALB path patterns sent to the service;
    the service with paths = [] receives every other request. desired_count 0
    defines a service without running it (for example before its image exists).
    Until Search and Chat are extracted, all three can run the Core image.
  EOT
  type = map(object({
    cpu           = number
    memory        = number
    desired_count = number
    paths         = list(string)
    priority      = number
  }))
  default = {
    core = {
      cpu = 512, memory = 1024, desired_count = 1, paths = [], priority = 0
    }
    search = {
      cpu = 512, memory = 1024, desired_count = 1, paths = ["/api/search", "/api/search/*"], priority = 10
    }
    chat = {
      cpu = 512, memory = 1024, desired_count = 1, paths = ["/ws", "/ws/*"], priority = 20
    }
  }
  validation {
    condition     = length([for s in values(var.services) : s if length(s.paths) == 0]) == 1
    error_message = "Exactly one service must have paths = [] (the default route)."
  }
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "health_check_path" {
  type    = string
  default = "/actuator/health"
}

variable "log_retention_days" {
  type    = number
  default = 14
}

# ---------------------------------------------------------------- scaling

variable "autoscaling" {
  description = <<-EOT
    Per-service target tracking. metric is cpu or requests (ALB requests per
    task per minute). Defaults from the 10/7 agenda: CPU 60%, 1 to 4 tasks;
    CUI ZIJIAN tunes them under WP10 after the staging load test.
  EOT
  type = map(object({
    min_capacity = number
    max_capacity = number
    metric       = string
    target       = number
  }))
  default = {
    core   = { min_capacity = 1, max_capacity = 4, metric = "cpu", target = 60 }
    search = { min_capacity = 1, max_capacity = 4, metric = "requests", target = 1000 }
    chat   = { min_capacity = 1, max_capacity = 4, metric = "cpu", target = 60 }
  }
  validation {
    condition     = alltrue([for a in values(var.autoscaling) : contains(["cpu", "requests"], a.metric)])
    error_message = "autoscaling metric must be cpu or requests."
  }
}

variable "enable_autoscaling" {
  description = "Null means production only, as in the proposal"
  type        = bool
  default     = null
}

# ---------------------------------------------------------------- CI/CD

variable "github_repository" {
  description = "owner/name allowed to assume the deploy role through OIDC"
  type        = string
  default     = "NUS-SE-34FT-GROUP4/ass"
}

variable "db_use_master_user" {
  description = "Services log in as the RDS master user. Set false once the per-service users from the WP8 grant script exist"
  type        = bool
  default     = true
}
