package prchecklist.services

import prchecklist.models._
import prchecklist.utils._

import scalaz.concurrent.Task

import scala.concurrent.duration._
import scala.language.postfixOps

import scalaj.http.HttpOptions.HttpOption
import scalaj.http.{ BaseHttp, HttpRequest, HttpResponse, HttpOptions }

trait GitHubHttpClientComponent extends HttpComponent {
  self: GitHubConfig =>

  class GitHubHttpClient(accessToken: String) extends Http {
    override def defaultHttpHeaders: Map[String, String] = {
      super.defaultHttpHeaders + ("Authorization" -> s"token $accessToken")
    }

    override def apply(url: String): HttpRequest = {
      super.apply(s"$githubApiBase$url")
    }
  }
}

trait GitHubServiceComponent {
  self: GitHubHttpClientComponent with RedisComponent with TypesComponent =>

  trait GitHubService {
    def githubAccessor: GitHubAccessible

    def githubHttpClient = new GitHubHttpClient(githubAccessor.accessToken)

    // https://developer.github.com/v3/repos/#get
    def getRepo(owner: String, name: String): Task[GitHubTypes.Repo] = {
      githubHttpClient.getJson[GitHubTypes.Repo](s"/repos/$owner/$name")
    }

    // https://developer.github.com/v3/issues/comments/#create-a-comment
    def addIssueComment(repoFullName: String, issueNumber: Int, body: String): Task[Unit] = {
      githubHttpClient.postJson(
        s"/repos/$repoFullName/issues/$issueNumber/comments",
        GitHubTypes.IssueComment(body)
      )
    }

    // https://developer.github.com/v3/repos/statuses/#create-a-status
    def setCommitStatus(repoFullName: String, sha: String, status: GitHubTypes.CommitStatus): Task[Unit] = {
      githubHttpClient.postJson(
        s"/repos/$repoFullName/statuses/$sha",
        status
      )
    }

    def getPullRequestWithCommits(repo: Repo, number: Int): Task[GitHubTypes.PullRequestWithCommits] = {
      redis.getOrUpdate(s"pull:${repo.fullName}:$number", 30 seconds) {
        for {
          pr <- githubHttpClient.getJson[GitHubTypes.PullRequest](s"/repos/${repo.fullName}/pulls/$number")
          commits <- getPullRequestCommitsPaged(repo, pr)
        } yield (GitHubTypes.PullRequestWithCommits(pr, commits), true)
      }
    }

    // https://developer.github.com/v3/pulls/#list-commits-on-a-pull-request
    // https://developer.github.com/v3/repos/commits/#list-commits-on-a-repository
    def getPullRequestCommitsPaged(repo: Repo, pullRequest: GitHubTypes.PullRequest, allCommits: List[GitHubTypes.Commit] = List(), page: Int = 1): Task[List[GitHubTypes.Commit]] = {
      // The document says "Note: The response includes a maximum of 250 commits"
      // but apparently it returns only 100 commits at maximum
      val commitsPerPage = 100

      if (page > 10) {
        throw new Error(s"page too far: $page")
      }

      githubHttpClient.getJson[List[GitHubTypes.Commit]](s"/repos/${repo.fullName}/commits?sha=${pullRequest.head.sha}&per_page=${commitsPerPage}&page=${page}").flatMap {
        pageCommits =>
          val commits = (allCommits ++ pageCommits).take(pullRequest.commits)
          if (commits.length < pullRequest.commits) {
            getPullRequestCommitsPaged(repo, pullRequest, commits, page + 1)
          } else {
            Task { commits }
          }
      }
    }

    def listReleasePullRequests(repo: Repo): Task[List[GitHubTypes.PullRequestRef]] = {
      githubHttpClient.getJson[List[GitHubTypes.PullRequestRef]](s"/repos/${repo.fullName}/pulls?base=master&state=all&per_page=20")
    }
  }

}
