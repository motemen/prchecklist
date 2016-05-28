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
 *     events:
 *       on_check:
 *         - default
 *       on_complete:
 *         - default
 *     channels:
 *       default:
 *         url: https://slack.com/xxxxx
 */
object ProjectConfig {
  object NotificationEvent {
    val EventOnCheck: NotificationEvent    = "on_check"
    val EventOnComplete: NotificationEvent = "on_complete"
  }

  type NotificationEvent = String
  type ChannelName = String

  case class Notification(
      events: Option[Map[NotificationEvent, List[ChannelName]]],
      channels: Map[ChannelName, Channel]) {

    def getChannels(event: NotificationEvent): List[Channel] = {
      val names = events match {
        case None      => List("default")
        case Some(map) => map.get(event) getOrElse List()
      }
      names.flatMap { name => channels.get(name).toList }
    }
  }

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

    def parseProjectConfig(source: String): ProjectConfig = {
      val mapper = new ObjectMapper(new YAMLFactory) with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      mapper.readValue[ProjectConfig](source)
    }

    // TODO: accept Checklist as an argument
    def loadProjectConfig(repo: Repo, ref: String): Future[Option[ProjectConfig]] = {
      import scala.concurrent.duration._
      import scala.language.postfixOps

      redis.getOrUpdate(s"projectConfig:${repo.fullName}:${ref}", 30 seconds) {
        for {
          yamlOption <- github.getFileContent(repo, "prchecklist.yml", ref)
        } yield {
          ( yamlOption.map(parseProjectConfig(_)), true )
        }
      }
    }
  }
}
