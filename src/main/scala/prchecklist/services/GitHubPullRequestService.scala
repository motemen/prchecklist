package prchecklist.services

import prchecklist.models._
import prchecklist.utils.HttpUtils

import scalaz.concurrent.Task
import scalaz.syntax.applicative._
import scalaz.syntax.std.option._

import com.github.tarao.nonempty.NonEmpty

class GitHubPullRequestService(val visitor: Visitor) {
  def mergedPullRequestNumbers(commits: List[JsonTypes.GitHubCommit]): List[Int] = {
    commits.flatMap {
      c =>
        """^Merge pull request #(\d+) """.r.findFirstMatchIn(c.commit.message) map {
          m => m.group(1).toInt
        }
    }
  }

  def getReleasePullRequest(repo: GitHubRepo, number: Int): Task[ReleasePullRequest] = {
    // TODO: access cache, getPullRequestFull if not exists
    val getPullRequestTask = Task.fromDisjunction {
      HttpUtils.httpRequestJson[JsonTypes.GitHubPullRequest](s"https://api.github.com/repos/${repo.fullName}/pulls/$number")
    }

    // TODO: paging
    val getPullRequestCommitsTask = Task.fromDisjunction {
      HttpUtils.httpRequestJson[List[JsonTypes.GitHubCommit]](s"https://api.github.com/repos/${repo.fullName}/pulls/$number/commits")
    }

    (getPullRequestTask |@| getPullRequestCommitsTask).tupled.flatMap {
      case (pr, commits) =>
        Task.fromDisjunction {
          NonEmpty.fromTraversable(mergedPullRequestNumbers(commits)).map {
            prNumbers =>
              // TODO: check if pr.base points to "master"
              ReleasePullRequest(repo, number, pr.title, pr.body, prNumbers)
          } \/> new Error("no feature PR")
        }
    }
  }
}
