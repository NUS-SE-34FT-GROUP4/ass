# AWS infrastructure (Terraform)

```
bootstrap/   S3 state bucket + DynamoDB lock table. Run once per AWS account.
env/         Everything else. One workspace per environment: staging, production.
```

## First time on a new account

```bash
cd infra/terraform/bootstrap
terraform init && terraform apply          # writes ../env/backend.hcl

cd ../env
terraform init -backend-config=backend.hcl
terraform workspace new staging
terraform apply
```

## Day to day

```bash
cd infra/terraform/env
terraform workspace select staging        # or production
terraform plan
terraform apply
terraform destroy                          # staging, when nobody is using it
```

Each workspace gets its own VPC (2 AZs, public and private subnets, one NAT
gateway by default). The state for workspace `staging` is stored at
`env:/staging/c2csectrade/terraform.tfstate` in the bucket and locked in
DynamoDB while a plan or apply runs.

Locking uses the DynamoDB table, as planned. Terraform 1.11+ warns that
`dynamodb_table` is deprecated in favour of S3-native `use_lockfile`; it still
works, and switching later only needs `use_lockfile = true` in backend.hcl.

bootstrap/ keeps its own state locally (bootstrap/terraform.tfstate, not
committed), since it creates the bucket remote state lives in. Losing it only
means importing the bucket and table again; it does not touch env/ state.

Cost note: the NAT gateway is billed by the hour whether used or not. Destroy
staging when it is idle.
