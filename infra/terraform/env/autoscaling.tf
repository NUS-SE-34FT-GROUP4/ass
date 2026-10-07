# Independent target tracking per service, so a search burst scales Search
# without scaling Core or Chat. On in production by default; the numbers come
# from var.autoscaling, which WP10 tunes after the load test.

locals {
  autoscaling_on = coalesce(var.enable_autoscaling, local.is_prod)
  scaled         = local.autoscaling_on ? { for k, v in var.autoscaling : k => v if contains(keys(var.services), k) } : {}
}

resource "aws_appautoscaling_target" "service" {
  for_each           = local.scaled
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.service[each.key].name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = each.value.min_capacity
  max_capacity       = each.value.max_capacity
}

resource "aws_appautoscaling_policy" "service" {
  for_each           = local.scaled
  name               = "${local.name}-${each.key}-${each.value.metric}"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.service[each.key].service_namespace
  resource_id        = aws_appautoscaling_target.service[each.key].resource_id
  scalable_dimension = aws_appautoscaling_target.service[each.key].scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = each.value.target
    scale_out_cooldown = 60
    scale_in_cooldown  = 300

    predefined_metric_specification {
      predefined_metric_type = each.value.metric == "cpu" ? "ECSServiceAverageCPUUtilization" : "ALBRequestCountPerTarget"
      resource_label = each.value.metric == "cpu" ? null : (
        "${aws_lb.main.arn_suffix}/${aws_lb_target_group.service[each.key].arn_suffix}"
      )
    }
  }
}
