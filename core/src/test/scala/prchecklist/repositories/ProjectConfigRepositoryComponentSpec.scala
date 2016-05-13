package prchecklist.repositories

import prchecklist.infrastructure
import prchecklist.models
import prchecklist.test
import prchecklist.utils.RunnableFuture

import org.scalatest._
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ProjectConfigRepositoryComponentSpec extends FunSuite with Matchers with MockitoSugar
  with ProjectConfigRepositoryComponent
  with GitHubRepositoryComponent
  with infrastructure.RedisComponent
  with infrastructure.GitHubHttpClientComponent
  with models.ModelsComponent
  with models.GitHubConfig
  with test.TestAppConfig
{
  def redis = new Redis
  def http = new Http

  val validProjectConfigYaml = """
notification:
  channels:
    default:
      url: https://slack.com/xxxx
"""

  test("parseProjectConfig") {
    val projConfRepository = new ProjectConfigRepository {
      override val github = mock[GitHubRepository]
    }

    val conf = projConfRepository.parseProjectConfig(validProjectConfigYaml).run
    conf.notification.channels("default").url shouldBe "https://slack.com/xxxx"
  }

  test("loadProjectConfig") {
    val projConfRepository = new ProjectConfigRepository {
      override val github = mock[GitHubRepository]

      when {
        github.getFileContent(any(), any(), any())
      } thenReturn {
        Future { validProjectConfigYaml }
      }
    }

    val repo = Repo(id = 1, owner = "test", name = "repo", defaultAccessToken = "")

    projConfRepository.loadProjectConfig(repo, "pull/42/head").run

    // TODO: check cache is used
    projConfRepository.loadProjectConfig(repo, "pull/42/head").run
  }
}
