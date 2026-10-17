# CloudWatch alarms (proposal: CloudWatch collects logs, metrics and alerts).
# Each service gets the same four alarms; RDS gets two. They notify an SNS
# topic, which emails var.alarm_email when it is set (the address stays out of
# the repository: pass it with -var or in the untracked tfvars).

variable "alarm_email" {
  description = "Email address subscribed to the alarm topic; null leaves the topic without subscribers"
  type        = string
  default     = null
}

resource "aws_sns_topic" "alarms" {
  name = "${local.name}-alarms"
}

resource "aws_sns_topic_subscription" "alarm_email" {
  count     = var.alarm_email == null ? 0 : 1
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

locals {
  alarm_actions = [aws_sns_topic.alarms.arn]
}

# Responses slower than the proposal's 2 s target, for 5 minutes running.
resource "aws_cloudwatch_metric_alarm" "latency_p95" {
  for_each            = var.services
  alarm_name          = "${local.name}-${each.key}-latency-p95"
  alarm_description   = "${each.key}: p95 target response time above 2 s for 5 minutes"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "TargetResponseTime"
  extended_statistic  = "p95"
  period              = 60
  evaluation_periods  = 5
  threshold           = 2
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.service[each.key].arn_suffix
  }
  alarm_actions = local.alarm_actions
  ok_actions    = local.alarm_actions
}

# The service itself failing requests, not clients sending bad ones.
resource "aws_cloudwatch_metric_alarm" "target_5xx" {
  for_each            = var.services
  alarm_name          = "${local.name}-${each.key}-5xx"
  alarm_description   = "${each.key}: more than 10 server errors in 5 minutes"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 10
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.service[each.key].arn_suffix
  }
  alarm_actions = local.alarm_actions
  ok_actions    = local.alarm_actions
}

# A task failing the liveness check for 3 minutes (ECS replaces it, but repeated
# replacement means something is wrong, as on 11 October).
resource "aws_cloudwatch_metric_alarm" "unhealthy_targets" {
  for_each            = var.services
  alarm_name          = "${local.name}-${each.key}-unhealthy"
  alarm_description   = "${each.key}: at least one unhealthy target for 3 minutes"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.service[each.key].arn_suffix
  }
  alarm_actions = local.alarm_actions
  ok_actions    = local.alarm_actions
}

# CPU staying high for 10 minutes: scaling should have absorbed it, so either
# the service is at its maximum task count or scaling is not working.
resource "aws_cloudwatch_metric_alarm" "cpu_high" {
  for_each            = var.services
  alarm_name          = "${local.name}-${each.key}-cpu-high"
  alarm_description   = "${each.key}: average CPU above 85% for 10 minutes"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 10
  threshold           = 85
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    ClusterName = aws_ecs_cluster.main.name
    ServiceName = aws_ecs_service.service[each.key].name
  }
  alarm_actions = local.alarm_actions
  ok_actions    = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "${local.name}-rds-cpu-high"
  alarm_description   = "RDS: average CPU above 80% for 10 minutes"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 10
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.mysql.identifier }
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  alarm_name          = "${local.name}-rds-storage-low"
  alarm_description   = "RDS: less than 2 GiB of free storage"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 2147483648
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.mysql.identifier }
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

output "alarm_topic_arn" {
  value = aws_sns_topic.alarms.arn
}
