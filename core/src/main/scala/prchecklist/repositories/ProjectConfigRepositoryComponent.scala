package prchecklist.repositories

import prchecklist.models
import prchecklist.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Represents project.yml
 *
 *   notification:
 *     channels:
 *       default:
 *         url: https://slack.com/xxxxx
 */
object ProjectConfig {
  case class Notification(channels: Map[String, Channel])

  case class Channel(url: String)
}

case class ProjectConfig(
  notification: ProjectConfig.Notification
)

trait ProjectConfigRepositoryComponent {
  this: GitHubRepositoryComponent
    with models.ModelsComponent
    with infrastructure.RedisComponent
      =>

  def projectConfigRepository(githubRepos: GitHubRepository): ProjectConfigRepository = new ProjectConfigRepository {
    override val github = githubRepos
  }

  trait ProjectConfigRepository {
    def github: GitHubRepository

    def parseProjectConfig(source: String): Future[ProjectConfig] = {
      val mapper = new ObjectMapper(new YAMLFactory) with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      Future { mapper.readValue[ProjectConfig](source) }
    }

    // TODO: accept Checklist as an argument
    def loadProjectConfig(repo: Repo, ref: String): Future[Option[ProjectConfig]] = {
      import scala.concurrent.duration._
      import scala.language.postfixOps

      redis.getOrUpdate(s"projectConfig:${repo.fullName}:${ref}", 30 seconds) {
        for {
          yaml <- github.getFileContent(repo, "prchecklist.yml", ref)
          conf <- yaml.map(parseProjectConfig(_).map(Some(_))) getOrElse Future.successful(None: Option[ProjectConfig])
        } yield (conf, true)
      }
    }
  }
}
