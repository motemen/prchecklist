package prchecklist.models

import prchecklist.utils.AppConfig

trait GitHubConfig extends AppConfig {
  def githubOrigin = new java.net.URI(s"https://$githubDomain")

  def githubApiBase =
    if (githubDomain == "github.com") "https://api.github.com" else s"https://$githubDomain/api/v3"
}
