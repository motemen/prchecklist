package prchecklist.services

import prchecklist.models._
import prchecklist.utils.GitHubHttpClient

import scalaz.concurrent.Task
import scalaz.syntax.applicative._

import scala.concurrent.duration._
import scala.language.postfixOps

class GitHubService(val githubHttpClient: GitHubHttpClient) {
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
    Redis.getOrUpdate(s"pull:${repo.fullName}:$number", 30 seconds) {
      val getPullRequestTask =
        githubHttpClient.getJson[GitHubTypes.PullRequest](s"/repos/${repo.fullName}/pulls/$number")

      val getPullRequestCommitsTask = getPullRequestCommitsPaged(repo, number)

      (getPullRequestTask |@| getPullRequestCommitsTask) apply {
        (pullRequest, commits) =>
          (GitHubTypes.PullRequestWithCommits(pullRequest, commits), true)
      }
    }
  }

  // https://developer.github.com/v3/pulls/#list-commits-on-a-pull-request
  def getPullRequestCommitsPaged(repo: Repo, number: Int, commits: List[GitHubTypes.Commit] = List(), page: Int = 1): Task[List[GitHubTypes.Commit]] = {
    // The document says "Note: The response includes a maximum of 250 commits"
    // but apparently it returns only 100 commits at maximum
    val commitsPerPage = 100

    githubHttpClient.getJson[List[GitHubTypes.Commit]](s"/repos/${repo.fullName}/pulls/$number/commits?per_page=${commitsPerPage}&page=${page}").flatMap {
      pageCommits =>
        if (page > 10) {
          throw new Error(s"page too far: $page")
        }
        if (pageCommits.size == commitsPerPage) {
          // go to next page
          getPullRequestCommitsPaged(repo, number, commits ++ pageCommits, page + 1)
        } else {
          Task { commits ++ pageCommits }
        }
    }
  }

  def listReleasePullRequests(repo: Repo): Task[List[GitHubTypes.PullRequest]] = {
    githubHttpClient.getJson[List[GitHubTypes.PullRequest]](s"/repos/${repo.fullName}/pulls?base=master&state=all&per_page=20")
  }
}
