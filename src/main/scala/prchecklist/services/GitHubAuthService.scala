package prchecklist.services

import prchecklist.models._
import prchecklist.utils.GitHubHttpClient
import prchecklist.utils.HttpUtils
import prchecklist.utils.UriStringContext._ // uri"..."

import java.net.URLEncoder

import scalaz.concurrent.Task

import org.slf4j.LoggerFactory

object GitHubAuthService extends GitHubConfig {
  val logger = LoggerFactory.getLogger("prchecklist.services.GitHubAuthService")

  def authorizationURL(redirectURI: String): String = {
    logger.debug(s"redirectURI: $redirectURI")
    uri"$githubOrigin/login/oauth/authorize?client_id=$githubClientId&redirect_uri=$redirectURI".toString
  }

  def authorize(code: String): Task[Visitor] = {
    Task {
      val accessTokenResBody = HttpUtils(
        s"https://$githubDomain/login/oauth/access_token"
      ).postForm(Seq(
          "client_id" -> githubClientId,
          "client_secret" -> githubClientSecret,
          "code" -> code
        )).asParamMap.body
      accessTokenResBody.get("access_token").getOrElse {
        throw new Error(s"could not get access_token $accessTokenResBody")
      }
    }.flatMap {
      accessToken =>
        for {
          user <- new GitHubHttpClient(accessToken).getJson[GitHubTypes.User]("/user") // FIXME
        } yield Visitor(user.login, accessToken)
    }
  }
}
