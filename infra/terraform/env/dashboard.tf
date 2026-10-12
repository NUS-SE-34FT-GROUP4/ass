# WP10: native metrics only; no application-side metric agent required.
resource "aws_cloudwatch_dashboard" "wp10" {
  dashboard_name = "${local.name}-wp10"
  dashboard_body = jsonencode({
    start = "-PT3H"
    widgets = flatten([
      [{ type      = "text", x = 0, y = 0, width = 24, height = 2,
        properties = { markdown = "# ${local.name}: load and scaling\nCompare identical workload windows. Missing data is not zero. ALB latency excludes WebSocket message latency; use k6 evidence for chat." }
      }],
      [for i, service in sort(keys(var.services)) : [
        { type = "metric", x = 0, y = 2 + i * 12, width = 12, height = 6,
          properties = { title = "${service}: CPU / memory (%)", region = var.region, period = 60, stat = "Average", view = "timeSeries",
            metrics = [
              ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.main.name, "ServiceName", aws_ecs_service.service[service].name],
              ["AWS/ECS", "MemoryUtilization", "ClusterName", aws_ecs_cluster.main.name, "ServiceName", aws_ecs_service.service[service].name]
            ]
          }
        },
        { type = "metric", x = 12, y = 2 + i * 12, width = 12, height = 6,
          properties = { title = "${service}: desired / running tasks", region = var.region, period = 60, stat = "Average", view = "timeSeries",
            metrics = [
              ["ECS/ContainerInsights", "DesiredTaskCount", "ClusterName", aws_ecs_cluster.main.name, "ServiceName", aws_ecs_service.service[service].name],
              ["ECS/ContainerInsights", "RunningTaskCount", "ClusterName", aws_ecs_cluster.main.name, "ServiceName", aws_ecs_service.service[service].name]
            ]
          }
        },
        { type = "metric", x = 0, y = 8 + i * 12, width = 12, height = 6,
          properties = { title = "${service}: target latency p50 / p95 (seconds)", region = var.region, period = 60, view = "timeSeries",
            metrics = [for percentile in ["p50", "p95"] : ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", aws_lb.main.arn_suffix, "TargetGroup", aws_lb_target_group.service[service].arn_suffix, { stat = percentile }]]
          }
        },
        { type = "metric", x = 12, y = 8 + i * 12, width = 12, height = 6,
          properties = { title = "${service}: requests per target / target 5xx", region = var.region, period = 60, stat = "Sum", view = "timeSeries",
            metrics = [
              ["AWS/ApplicationELB", "RequestCountPerTarget", "TargetGroup", aws_lb_target_group.service[service].arn_suffix, { label = "Requests per target / minute" }],
              ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", aws_lb.main.arn_suffix, "TargetGroup", aws_lb_target_group.service[service].arn_suffix, { label = "Target 5xx count", yAxis = "right" }]
            ]
          }
        }
      ]]
    ])
  })
}

output "wp10_dashboard_name" {
  value = aws_cloudwatch_dashboard.wp10.dashboard_name
}
