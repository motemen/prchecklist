package prchecklist.services

import java.net.URI

import org.json4s._
import prchecklist.models._
import prchecklist.utils.HttpUtils

import com.redis._

import scalaz.concurrent.Task
import scalaz.syntax.applicative._

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
      // TODO: access cache, getPullRequestFull if not exists
      val getPullRequestTask = Task.fromDisjunction {
        HttpUtils.httpRequestJson[JsonTypes.GitHubPullRequest](s"https://api.github.com/repos/${repo.fullName}/pulls/$number")
      }

      // TODO: paging
      val getPullRequestCommitsTask = Task.fromDisjunction {
        HttpUtils.httpRequestJson[List[JsonTypes.GitHubCommit]](s"https://api.github.com/repos/${repo.fullName}/pulls/$number/commits")
      }

      (getPullRequestTask |@| getPullRequestCommitsTask) apply {
        case (pr, commits) =>
          val prNumbers = mergedPullRequestNumbers(commits)
          // TODO: check if pr.base points to "master"
          // TODO: check if prNumbers.nonEmpty
          val releasePR = ReleasePullRequest(repo, number, pr.title, pr.body, prNumbers)
          redis.set(redisKey, json4s.native.Serialization.write(releasePR))
          releasePR
      }
    }
  }
}
