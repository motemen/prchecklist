package prchecklist.services

import java.io.ByteArrayInputStream
import java.net.URI

import prchecklist.models._
import prchecklist.utils.GitHubHttpClient

import org.json4s._

import com.redis._
import com.redis.serialization.Parse

import scalaz.concurrent.Task
import scalaz.syntax.applicative._

class GitHubPullRequestService(val githubHttpClient: GitHubHttpClient) extends GitHubConfig with GitHubPullRequestUtils {
  def getReleasePullRequest(repo: GitHubRepo, number: Int): Task[ReleasePullRequest] = {
    import org.json4s
    import org.json4s.native.JsonMethods

    implicit val formats = json4s.native.Serialization.formats(json4s.NoTypeHints)

    implicit def redisParseJson[A](implicit formats: Formats, mf: Manifest[A]) = Parse {
      b => JsonMethods.parse(new ByteArrayInputStream(b)).extract[A]
    }

    val redisURL = new URI(System.getProperty("redis.url", "redis://127.0.0.1:6379"))
    val redis = new RedisClient(host = redisURL.getHost, port = redisURL.getPort)
    Option(redisURL.getUserInfo).map(_.split(":", 2)) match {
      case Some(Array(_, password)) => redis.auth(password)
      case _ =>
    }

    val redisKey = s"pull:${repo.fullName}:$number"
    redis.get[ReleasePullRequest](redisKey) match {
      case Some(pr) =>
        Task.now(pr)

      case None =>
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
                if (pr.base.repo.isPublic) {
                  redis.set(redisKey, json4s.native.Serialization.write(releasePR))
                }
                Task.now(releasePR)
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
