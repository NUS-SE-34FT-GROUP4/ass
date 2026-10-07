terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Filled in by backend.hcl, which bootstrap/ writes. One state file per
  # workspace: staging and production live under env:/<workspace>/ in the bucket.
  backend "s3" {}
}

provider "aws" {
  region = var.region
  default_tags {
    tags = {
      Project     = "c2csectrade"
      Environment = terraform.workspace
      ManagedBy   = "terraform"
    }
  }
}
