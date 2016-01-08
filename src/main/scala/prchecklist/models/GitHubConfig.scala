package prchecklist.models

object GitHubConfig {
  private object config extends GitHubConfig

  def clientId = config.githubClientId
  def clientSecret = config.githubClientSecret
  def domain = config.githubDomain
  def apiBase = config.githubApiBase
}

trait GitHubConfig {
  val githubClientId = System.getProperty("github.clientId").ensuring(_ != null, "github.clientId must be defined")
  val githubClientSecret = System.getProperty("github.clientSecret").ensuring(_ != null, "github.clientSecret must be defined")
  val githubDomain = System.getProperty("github.domain", "github.com")

  def githubOrigin = s"https://$githubDomain"

  def githubApiBase =
    if (githubDomain == "github.com") "https://api.github.com" else s"https://$githubDomain/api/v3"
}
