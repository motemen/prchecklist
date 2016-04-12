package prchecklist.models

import prchecklist.utils.AppConfig

import java.net.URI

trait GitHubConfig {
  self: AppConfig =>

  def githubOrigin = new java.net.URI(s"https://$githubDomain")

  def githubApiBase =
    if (githubDomain == "github.com") "https://api.github.com" else s"https://$githubDomain/api/v3"

  def avatarUrl(u: prchecklist.models.UserLike) =
    s"${githubOrigin}/${u.login}.png"
}
