package prchecklist.infrastructure

import prchecklist.models.GitHubConfig

import scalaj.http.HttpRequest

/**
  * Created by motemen on 2016/05/02.
  */
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
