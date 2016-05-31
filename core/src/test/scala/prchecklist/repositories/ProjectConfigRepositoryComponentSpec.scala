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
  with test.WithTestDatabase
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

    val conf = projConfRepository.parseProjectConfig(validProjectConfigYaml)
    conf.notification.channels("default").url shouldBe "https://slack.com/xxxx"
  }

  test("loadProjectConfig should use Redis cache") {
    val projConfRepository = new ProjectConfigRepository {
      override val github = mock[GitHubRepository]

      when {
        github.getFileContent(any(), any(), any())
      } thenReturn {
        Future { Some(validProjectConfigYaml) }
      }
    }

    val repo = Repo(id = 1, owner = "test", name = "repo", defaultAccessToken = "")

    projConfRepository.loadProjectConfig(repo, "pull/42/head").run

    // This should not call getFileContent, as redis cache should be used
    projConfRepository.loadProjectConfig(repo, "pull/42/head").run

    verify(projConfRepository.github, times(1)).getFileContent(any(), any(), any())
  }

  test("loadProjectConfig: when the yaml not found") {
    val projConfRepository = new ProjectConfigRepository {
      override val github = mock[GitHubRepository]

      when {
        github.getFileContent(any(), any(), any())
      } thenReturn {
        Future.successful(None)
      }
    }

    val repo = Repo(id = 1, owner = "test", name = "repo", defaultAccessToken = "")

    projConfRepository.loadProjectConfig(repo, "pull/43/head").run

    verify(projConfRepository.github, times(1)).getFileContent(any(), any(), any())

    // This should not call getFileContent, as redis cache should be used
    projConfRepository.loadProjectConfig(repo, "pull/43/head").run

    verify(projConfRepository.github, times(1)).getFileContent(any(), any(), any())
  }

  test("Notification#getChannelsWithAssociatedEvents") {
    ProjectConfig.Notification(
      events = None,
      channels = Map("default" -> ProjectConfig.Channel(url = "test://default"))
    ).getChannelsWithAssociatedEvents(List("on_check", "on_complete")) shouldBe
      Map(
        ProjectConfig.Channel(url = "test://default") -> Set("on_check", "on_complete")
      )

    ProjectConfig.Notification(
      events = Some(
        Map(
          "on_complete" -> List("default", "ch_completion"),
          "on_check" -> List("default")
        )
      ),
      channels = Map(
        "default" -> ProjectConfig.Channel(url = "test://default"),
        "ch_completion" -> ProjectConfig.Channel(url = "test://ch_completion")
      )
    ).getChannelsWithAssociatedEvents(Set("on_check", "on_complete")) shouldBe
      Map(
        ProjectConfig.Channel(url = "test://default") -> Set("on_check", "on_complete"),
        ProjectConfig.Channel(url = "test://ch_completion") -> Set("on_complete")
      )
  }
}
