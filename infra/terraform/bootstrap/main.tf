# One-off: creates the S3 bucket that holds Terraform state and the DynamoDB
# table that locks it. Run once per AWS account, with local state, before env/.

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "c2csectrade", ManagedBy = "terraform" }
  }
}

variable "region" {
  type    = string
  default = "ap-southeast-1"
}

data "aws_caller_identity" "current" {}

locals {
  # Bucket names are global, so the account ID keeps this one unique.
  bucket = "c2csectrade-tfstate-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket" "state" {
  bucket = local.bucket
}

# Every state write is kept, so a bad apply can be rolled back to the last good state.
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# State contains resource attributes such as database endpoints; never public.
resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "lock" {
  name         = "c2csectrade-tflock"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}

# Written for env/: terraform init -backend-config=backend.hcl
resource "local_file" "backend_config" {
  filename = "${path.module}/../env/backend.hcl"
  content  = <<-HCL
    bucket         = "${aws_s3_bucket.state.id}"
    key            = "c2csectrade/terraform.tfstate"
    region         = "${var.region}"
    dynamodb_table = "${aws_dynamodb_table.lock.name}"
    encrypt        = true
  HCL
}

output "state_bucket" {
  value = aws_s3_bucket.state.id
}

output "lock_table" {
  value = aws_dynamodb_table.lock.name
}
