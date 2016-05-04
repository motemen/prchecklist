package prchecklist.repositories

import prchecklist.models

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scalaz.concurrent.Task

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
      =>

  def projectConfigRepository(githubRepos: GitHubRepository): ProjectConfigRepository = new ProjectConfigRepository {
    override val github = githubRepos
  }

  trait ProjectConfigRepository {
    def github: GitHubRepository

    def parseProjectConfig(source: String): Task[ProjectConfig] = {
      val mapper = new ObjectMapper(new YAMLFactory) with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      Task { mapper.readValue[ProjectConfig](source) }
    }

    def loadProjectConfig(repo: Repo, ref: String): Task[ProjectConfig] = {
      for {
        yaml <- github.getFileContent(repo, "prchecklist.yml", ref)
        conf <- parseProjectConfig(yaml)
      } yield conf
    }
  }
}
