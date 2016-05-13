package prchecklist.infrastructure

import prchecklist.models.GitHubConfig
import prchecklist.utils.HttpComponent

import scalaj.http.HttpRequest

trait GitHubHttpClientComponent extends HttpComponent {
  self: GitHubConfig =>

  def githubHttpClient(accessToken: String): GitHubHttpClient = new GitHubHttpClient(accessToken)

  class GitHubHttpClient(accessToken: String) extends Http {
    override def defaultHttpHeaders: Map[String, String] = {
      super.defaultHttpHeaders + ("Authorization" -> s"token $accessToken")
    }

    override def apply(url: String): HttpRequest = {
      super.apply(s"$githubApiBase$url")
    }
  }
}
