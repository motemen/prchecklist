package prchecklist.services

import prchecklist.models._
import prchecklist.utils.HttpComponent
import prchecklist.utils.AppConfig
import prchecklist.utils.UriStringContext._ // uri"..."

import java.net.URLEncoder

import scalaz.concurrent.Task

import org.slf4j.LoggerFactory

trait GitHubAuthServiceComponent {
  self: GitHubConfig with AppConfig with TypesComponent with GitHubHttpClientComponent with HttpComponent =>

  def githubAuthService: GitHubAuthService

  class GitHubAuthService {
    val logger = LoggerFactory.getLogger("prchecklist.services.GitHubAuthService")

    def authorizationURL(redirectURI: String): String = {
      logger.debug(s"redirectURI: $redirectURI")
      uri"$githubOrigin/login/oauth/authorize?client_id=$githubClientId&redirect_uri=$redirectURI".toString
    }

    def authorize(code: String): Task[Visitor] = {
      Task {
        val accessTokenRes = http(
          s"https://$githubDomain/login/oauth/access_token"
        ).postForm(Seq(
            "client_id" -> githubClientId,
            "client_secret" -> githubClientSecret,
            "code" -> code
          )).asParamMap
        if (!accessTokenRes.isSuccess) {
          throw new Error(s"could not get access_token status=${accessTokenRes.statusLine}")
        }
        accessTokenRes.body.get("access_token").getOrElse {
          throw new Error(s"could not get access_token [$accessTokenRes.body]")
        }
      }.flatMap {
        token =>
          for {
            user <- new GitHubHttpClient(token).getJson[GitHubTypes.User]("/user") // FIXME
          } yield Visitor(user.login, token)
      }
    }
  }
}
