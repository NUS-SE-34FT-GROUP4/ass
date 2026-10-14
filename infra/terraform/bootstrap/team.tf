# Console access for team members who need to look, not change: the CloudWatch
# dashboard and logs during the load test, and screenshots for the slides.
# Deployments stay with GitHub Actions (OIDC) and Terraform applies with M1.
#
# No passwords here, so none ends up in state. After apply, create each
# member's one-time password (they must change it at first sign-in):
#   aws iam create-login-profile --user-name <name> --password-reset-required \
#     --password "$(openssl rand -base64 18 | tr -d '/+=')Aa1#"

variable "readonly_members" {
  description = "IAM users given read-only console access (ViewOnlyAccess and CloudWatch read)"
  type        = set(string)
  default     = ["cui-zijian"]
}

resource "aws_iam_group" "team_readonly" {
  name = "c2csectrade-team-readonly"
}

# Lists and configuration of every service, without reading data or secrets.
resource "aws_iam_group_policy_attachment" "view_only" {
  group      = aws_iam_group.team_readonly.name
  policy_arn = "arn:aws:iam::aws:policy/job-function/ViewOnlyAccess"
}

# Dashboards, metrics, alarms and log events.
resource "aws_iam_group_policy_attachment" "cloudwatch_read" {
  group      = aws_iam_group.team_readonly.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchReadOnlyAccess"
}

# Until a member has signed in with MFA, the only things they can do are set
# their own password and register their own MFA device (AWS's documented
# "force MFA" pattern).
data "aws_iam_policy_document" "require_mfa" {
  statement {
    sid = "ManageOwnPasswordAndMfa"
    actions = [
      "iam:ChangePassword",
      "iam:GetUser",
      "iam:GetAccountPasswordPolicy",
      "iam:CreateVirtualMFADevice",
      "iam:EnableMFADevice",
      "iam:ListMFADevices",
      "iam:ResyncMFADevice",
    ]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/$${aws:username}",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:mfa/*",
    ]
  }

  statement {
    sid    = "DenyEverythingElseWithoutMfa"
    effect = "Deny"
    not_actions = [
      "iam:ChangePassword",
      "iam:GetUser",
      "iam:GetAccountPasswordPolicy",
      "iam:CreateVirtualMFADevice",
      "iam:EnableMFADevice",
      "iam:ListMFADevices",
      "iam:ResyncMFADevice",
      "sts:GetSessionToken",
    ]
    resources = ["*"]
    condition {
      test     = "BoolIfExists"
      variable = "aws:MultiFactorAuthPresent"
      values   = ["false"]
    }
  }
}

resource "aws_iam_group_policy" "require_mfa" {
  name   = "require-mfa"
  group  = aws_iam_group.team_readonly.name
  policy = data.aws_iam_policy_document.require_mfa.json
}

resource "aws_iam_user" "readonly" {
  for_each      = var.readonly_members
  name          = each.value
  force_destroy = true # removing a member also removes their password and MFA device
}

resource "aws_iam_user_group_membership" "readonly" {
  for_each = var.readonly_members
  user     = aws_iam_user.readonly[each.value].name
  groups   = [aws_iam_group.team_readonly.name]
}

output "console_sign_in_url" {
  value = "https://${data.aws_caller_identity.current.account_id}.signin.aws.amazon.com/console"
}
