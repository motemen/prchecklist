package prchecklist.services

import prchecklist.models._
import prchecklist.utils.HttpUtils

import scalaz.concurrent.Task
import scalaz.syntax.applicative._

object GitHubPullRequestService {
  def getPullRequestFull(repo: GitHubRepository, number: Int): Task[GitHubPullRequestFull] = {
    val getPullRequestTask = Task.fromDisjunction {
      HttpUtils.httpRequestJson[JsonTypes.GitHubPullRequest](s"https://api.github.com/repos/${repo.fullName}/pulls/$number")
    }
    // TODO: paging
    val getPullRequestCommitsTask = Task.fromDisjunction {
      HttpUtils.httpRequestJson[List[JsonTypes.GitHubCommit]](s"https://api.github.com/repos/${repo.fullName}/pulls/$number/commits")
    }

    (getPullRequestTask |@| getPullRequestCommitsTask) apply {
      case (pr, commits) =>
        GitHubPullRequestFull(repo, pr, commits)
    }
  }

  def getReleasePullRequest(repo: GitHubRepository, number: Int): Task[ReleasePullRequest] = {
    // TODO: access cache, getPullRequestFull if not exists
    ???
  }
}
