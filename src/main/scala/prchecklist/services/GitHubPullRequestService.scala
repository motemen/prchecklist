package prchecklist.services

import prchecklist.models._
import prchecklist.utils.GitHubHttpClient

import org.json4s

import scalaz.concurrent.Task
import scalaz.syntax.applicative._

class GitHubPullRequestService(val githubHttpClient: GitHubHttpClient) extends GitHubConfig with GitHubPullRequestUtils {
  def getReleasePullRequest(repo: GitHubRepo, number: Int): Task[ReleasePullRequest] = {
    implicit val formats = json4s.native.Serialization.formats(json4s.NoTypeHints)

    Redis.getOrUpdate(s"pull:${repo.fullName}:$number") {
      val getPullRequestTask = Task.fromDisjunction {
        githubHttpClient.requestJson[JsonTypes.GitHubPullRequest](s"$githubApiBase/repos/${repo.fullName}/pulls/$number")
      }

      // TODO: paging
      val getPullRequestCommitsTask = Task.fromDisjunction {
        githubHttpClient.requestJson[List[JsonTypes.GitHubCommit]](s"$githubApiBase/repos/${repo.fullName}/pulls/$number/commits?per_page=100")
      }

      (getPullRequestTask |@| getPullRequestCommitsTask).tupled.flatMap {
        case (pr, commits) =>
          val featurePRs = mergedPullRequests(commits)
          validateReleasePullRequest(pr, featurePRs) match {
            case Some(msg) =>
              Task.fail(new Error(msg))

            case None =>
              val releasePR = ReleasePullRequest(repo, number, pr.title, pr.body, featurePRs)
              Task.now((releasePR, pr.base.repo.isPublic))
          }
      }
    }
  }
}

trait GitHubPullRequestUtils {
  def mergedPullRequests(commits: List[JsonTypes.GitHubCommit]): List[PullRequestReference] = {
    commits.flatMap {
      c =>
        """^Merge pull request #(\d+) from [^\n]+\s+(.+)""".r.findFirstMatchIn(c.commit.message) map {
          m => PullRequestReference(m.group(1).toInt, m.group(2))
        }
    }
  }

  def validateReleasePullRequest(pr: JsonTypes.GitHubPullRequest, featurePRs: List[PullRequestReference]): Option[String] = {
    if (pr.base.ref != "master") {
      Some("not a pull request to master")
    } else if (featurePRs.isEmpty) {
      Some("no feature pull requests merged")
    } else {
      None
    }
  }
}
