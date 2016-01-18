package prchecklist.services

import prchecklist.models._

import com.github.tarao.slickjdbc.interpolation.SQLInterpolation

import slick.driver.PostgresDriver.api.DBIO
import slick.driver.PostgresDriver.api.jdbcActionExtensionMethods // q.transactionally

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object GitHubRepoService extends SQLInterpolation {
  def get(owner: String, name: String): Future[Option[GitHubRepo]] = {
    val db = Database.get
    db.run(getQuery(owner, name))
  }

  private def getQuery(owner: String, name: String): DBIO[Option[GitHubRepo]] = {
    sql"""
      | SELECT id, default_access_token
      | FROM github_repos
      | WHERE owner = ${owner}
      |   AND name = ${name}
      | LIMIT 1
    """.as[(Int, String)].map(_.headOption.map {
      case (id, defaultAccessToken) => GitHubRepo(id, owner, name, defaultAccessToken)
    })
  }

  def create(owner: String, name: String, defaultAccessToken: String): Future[(GitHubRepo, Boolean)] = {
    val db = Database.get

    val q = getQuery(owner, name).flatMap {
      case Some(repo) =>
        DBIO.successful((repo, false))

      case None =>
        sql"""
          | INSERT INTO github_repos
          |   (owner, name, default_access_token)
          | VALUES
          |   (${owner}, ${name}, ${defaultAccessToken})
          | RETURNING id
        """.as[Int].head.map {
          id => (GitHubRepo(id, owner, name, defaultAccessToken), true)
        }
    }

    db.run(q.transactionally)
  }
}
