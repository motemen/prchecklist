package prchecklist.services

import prchecklist.models._
import prchecklist.utils.GitHubHttpClient
import prchecklist.utils.HttpUtils._

import java.net.URLEncoder

import scalaz.concurrent.Task
import scalaz.syntax.std.option._

import org.slf4j.LoggerFactory

object GitHubAuthService extends GitHubConfig {
  val logger = LoggerFactory.getLogger("prchecklist.services.GitHubAuthService")

  def authorizationURL(redirectURI: String): String = {
    logger.debug(s"redirectURI: $redirectURI")
    s"https://$githubDomain/login/oauth/authorize?client_id=$githubClientId&redirect_uri=${URLEncoder.encode(redirectURI, "UTF-8")}"
  }

  def authorize(code: String): Task[Visitor] = {
    Task.fromDisjunction {
      for {
        accessTokenResBody <- request(
          s"https://$githubDomain/login/oauth/access_token",
          _.asParamMap,
          _.postForm(Seq(
            "client_id" -> githubClientId,
            "client_secret" -> githubClientSecret,
            "code" -> code
          ))
        )
        accessToken <- accessTokenResBody.get("access_token") \/> new Error(s"could not get access_token $accessTokenResBody")
      } yield accessToken
    }.flatMap {
      accessToken =>
        for {
          user <- new GitHubHttpClient(accessToken).getJson[JsonTypes.GitHubUser]("/user") // FIXME
        } yield Visitor(user.login, accessToken)
    }
  }
}
