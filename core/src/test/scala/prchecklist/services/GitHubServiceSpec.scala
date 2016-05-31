package prchecklist.services

import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.{Matchers => MockitoMatchers}
import org.mockito.Matchers._
import prchecklist.infrastructure.{GitHubHttpClientComponent, HttpComponent, RedisComponent}
import prchecklist.models._
import prchecklist.repositories.GitHubRepositoryComponent
import prchecklist.test._
import prchecklist.utils._
import prchecklist.utils.RunnableFuture

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class GitHubServiceSpec extends FunSuite with Matchers with MockitoSugar
    with GitHubRepositoryComponent
    with GitHubHttpClientComponent
    with RedisComponent
    with TestAppConfig
    with ModelsComponent
    with GitHubConfig
    with HttpComponent {

  override def redis = new Redis

  override def http = new Http

  test("getPullRequestWithCommits") {

    val mockedClient = mock[GitHubHttpClient]

    when {
      mockedClient.getJson[GitHubTypes.PullRequest](MockitoMatchers.eq("/repos/test-owner/test-name/pulls/47"))(any(), any(), any())
    } thenReturn {
      Future.successful {
        Factory.createGitHubPullRequest.copy(number = 47, commits = 1)
      }
    }

    when {
      mockedClient.getJson[List[GitHubTypes.Commit]](MockitoMatchers.eq("/repos/test-owner/test-name/pulls/47/commits?per_page=1&page=1"))(any(), any(), any())
    } thenReturn {
      Future.successful { List(Factory.createGitHubCommit) }
    }

    val githubRepository = new GitHubRepository {
      override val client = mockedClient
    }

    val prWithCommits = githubRepository.getPullRequestWithCommits(Repo(0, "test-owner", "test-name", ""), 47).run

    // TODO: redis
  }
}
