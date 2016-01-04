package prchecklist.services

import java.net.URI

import org.json4s._
import prchecklist.models._
import prchecklist.utils.HttpUtils

import com.redis._

import scalaz.concurrent.Task
import scalaz.syntax.applicative._

class GitHubPullRequestService(val visitor: Visitor) extends GitHubConfig {
  def accessToken = visitor.accessToken

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

  def getReleasePullRequest(repo: GitHubRepo, number: Int): Task[ReleasePullRequest] = {
    import org.json4s
    import org.json4s.native.JsonMethods

    implicit val formats = json4s.native.Serialization.formats(json4s.NoTypeHints)

    val redisURL = new URI(System.getProperty("redis.url", "redis://127.0.0.1:6379"))
    val redis = new RedisClient(host = redisURL.getHost, port = redisURL.getPort)
    val redisKey = s"pull:${repo.fullName}:$number"
    // TODO: redis parser
    redis.get[String](redisKey).flatMap {
      s => JsonMethods.parse(s).extractOpt[ReleasePullRequest]
    }.map {
      pr => Task.now(pr)
    }.getOrElse {
      val getPullRequestTask = Task.fromDisjunction {
        HttpUtils.httpRequestJson[JsonTypes.GitHubPullRequest](s"$githubApiBase/repos/${repo.fullName}/pulls/$number", _.header("Authorization", s"token $accessToken"))
      }

      // TODO: paging
      val getPullRequestCommitsTask = Task.fromDisjunction {
        HttpUtils.httpRequestJson[List[JsonTypes.GitHubCommit]](s"$githubApiBase/repos/${repo.fullName}/pulls/$number/commits", _.header("Authorization", s"token $accessToken"))
      }

      (getPullRequestTask |@| getPullRequestCommitsTask).tupled.flatMap {
        case (pr, commits) =>
          val featurePRs = mergedPullRequests(commits)
          validateReleasePullRequest(pr, featurePRs) match {
            case Some(msg) =>
              Task.fail(new Error(msg))

            case None =>
              val releasePR = ReleasePullRequest(repo, number, pr.title, pr.body, featurePRs)
              redis.set(redisKey, json4s.native.Serialization.write(releasePR))
              Task.now(releasePR)
          }
      }
    }
  }
}
