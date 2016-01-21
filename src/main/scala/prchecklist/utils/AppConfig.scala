package prchecklist.utils

trait AppConfig {
  private def envOrSystemProp(envKey: String, propKey: String, default: => String): String = {
    Option(System.getenv(envKey)) orElse Option(System.getProperty(propKey)) getOrElse default
  }

  private def envOrSystemProp(envKey: String, propKey: String): String =
    envOrSystemProp(envKey, propKey, { throw new Error(s"$propKey or $envKey must be set") })

  val githubClientId = envOrSystemProp("GITHUB_CLIENT_ID", "github.clientId")

  val githubClientSecret = envOrSystemProp("GITHUB_CLIENT_SECRET", "github.clientSecret")

  val githubDomain = envOrSystemProp("GITHUB_DOMAIN", "github.domain", "github.com")

  val githubDefaultToken = envOrSystemProp("GITHUB_DEFAULT_TOKEN", "github.defaultToken", "")

  val databaseUrl = envOrSystemProp("DATABASE_URL", "database.url", "jdbc:postgresql:prchecklist_local")

  val redisUrl = envOrSystemProp("REDIS_URL", "redis.url", "redis://127.0.0.1:6379")

  val httpAllowUnsafeSSL = envOrSystemProp("HTTP_ALLOW_UNSAFE_SSL", "http.allowUnsafeSSL", "") == "true"
}
