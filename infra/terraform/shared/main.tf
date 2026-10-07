# Account-wide resources that staging and production share: the image
# registries (both environments deploy the same image from the same commit) and
# the GitHub OIDC identity provider (one per account).
#
#   terraform init -backend-config=../env/backend.hcl -backend-config="key=c2csectrade/shared.tfstate"
#   terraform apply

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
  backend "s3" {}
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "c2csectrade", Environment = "shared", ManagedBy = "terraform" }
  }
}

variable "region" {
  type    = string
  default = "ap-southeast-1"
}

variable "services" {
  description = "One ECR repository per microservice"
  type        = list(string)
  default     = ["core", "search", "chat"]
}

variable "images_to_keep" {
  description = "Older images beyond this count are expired from each repository"
  type        = number
  default     = 30
}

resource "aws_ecr_repository" "service" {
  for_each = toset(var.services)
  name     = "c2csectrade/${each.key}"

  # CI tags images with the commit SHA; a tag never moves to another build.
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = aws_ecr_repository.service
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last ${var.images_to_keep} images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = var.images_to_keep
      }
      action = { type = "expire" }
    }]
  })
}

# GitHub Actions exchanges its OIDC token for short-lived AWS credentials, so no
# access keys are stored in the repository. The roles that trust this provider
# are per environment, in env/github.tf.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

output "ecr_repository_urls" {
  value = { for k, r in aws_ecr_repository.service : k => r.repository_url }
}

output "github_oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.github.arn
}
