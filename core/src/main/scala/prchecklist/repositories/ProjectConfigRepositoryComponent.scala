package prchecklist.repositories

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

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
  // this: GitHubRepositoryComponent =>

  trait ProjectConfigRepository {
    // def github: GitHubRepository

    def parseProjectConfig(source: String): ProjectConfig = {
      val mapper = new ObjectMapper(new YAMLFactory) with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      mapper.readValue[ProjectConfig](source)
    }

    // def loadProjectConfig: ProjectConfig
  }
}
