# Account-wide spending alarm. The email is passed at apply time
# (-var alert_email=...) so it stays out of the public repository.

variable "alert_email" {
  description = "Where budget alerts are sent"
  type        = string
}

variable "monthly_budget_usd" {
  type    = number
  default = 10
}

resource "aws_budgets_budget" "monthly" {
  name         = "c2csectrade-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # Count usage before credits. The account has promotional credits, and with
  # them netted off the alarm would stay silent until the credits ran out.
  cost_types {
    include_credit = false
    include_refund = false
  }

  # Actual spend has passed the limit.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  # AWS forecasts the month will end above the limit: a warning while
  # there is still time to destroy something.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }
}
