import prchecklist._
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    Map(
      "GITHUB_CLIENT_ID" -> "github.clientId",
      "GITHUB_CLIENT_SECRET" -> "github.clientSecret",
      "DATABASE_URL" -> "database.url",
      "REDIS_URL" -> "redis.url"
    ) foreach {
        case (env, key) =>
          Option(System.getenv(env)) foreach {
            envValue => System.setProperty(key, envValue)
          }
      }

    context.mount(new MyScalatraServlet, "/*")
  }
}
