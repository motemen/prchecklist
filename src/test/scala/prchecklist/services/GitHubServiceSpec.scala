package prchecklist.services

import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.mock.MockitoSugar

import org.mockito.Mockito._

import prchecklist.models._
import prchecklist.utils.GitHubHttpClient
import prchecklist.test._

import scalaz.concurrent.Task

class GitHubServiceSpec extends FunSuite with MockitoSugar {
  test("getPullRequestWithCommits") {
    val client = mock[GitHubHttpClient]

    when(
      client.getJson[GitHubTypes.PullRequest]("/repos/test-owner/test-name/pulls/47")
    ) thenReturn Task {
        Factory.createGitHubPullRequest
      }

    when(
      client.getJson[List[GitHubTypes.Commit]]("/repos/test-owner/test-name/pulls/47/commits?per_page=100")
    ) thenReturn Task {
        List()
      }

    val githubService = new GitHubService(client)
    val prWithCommits = githubService.getPullRequestWithCommits(Repo(0, "test-owner", "test-name", ""), 47).run

    // TODO: redis
  }
}
