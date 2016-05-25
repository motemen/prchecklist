package prchecklist.repositories

import prchecklist.models
import prchecklist.models.ProjectConfig
import prchecklist.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

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
