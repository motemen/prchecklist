package prchecklist.repositories

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

object ProjectConfig {
  case class Channel(url: String)
}

case class ProjectConfig(
  channels: Map[String, ProjectConfig.Channel]
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
