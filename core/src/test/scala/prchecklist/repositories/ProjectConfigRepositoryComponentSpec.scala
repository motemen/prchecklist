package prchecklist.repositories

import prchecklist.infrastructure
import prchecklist.models
import prchecklist.test

import org.scalatest._
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._

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

  test("parseProjectConfig") {
    val repo = new ProjectConfigRepository {
      override val github = mock[GitHubRepository]
    }

    val conf = repo.parseProjectConfig("""
notification:
  channels:
    default:
      url: https://slack.com/xxxx
""").run
    conf.notification.channels("default").url shouldBe "https://slack.com/xxxx"
  }
}
