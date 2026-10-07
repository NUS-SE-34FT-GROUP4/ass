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
