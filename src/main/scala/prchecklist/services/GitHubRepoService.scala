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

    val q = sql"""
      | SELECT id
      | FROM github_repos
      | WHERE owner = $owner
      |   AND name = $name
      | LIMIT 1
    """.as[Int].headOption.map {
      _.map {
        id =>
          GitHubRepo(id, owner, name)
      }
    }

    db.run(q)
  }

  def create(owner: String, name: String): Future[(GitHubRepo, Boolean)] = {
    val db = Database.get

    val q = sql"""
      | SELECT id
      | FROM github_repos
      | WHERE owner = ${owner}
      |   AND name = ${name}
      | LIMIT 1
    """.as[Int].map(_.headOption).flatMap {
      case Some(v) => DBIO.successful((v, false))
      case None =>
        sql"""
          | INSERT INTO github_repos
          |   (owner, name)
          | VALUES
          |   (${owner}, ${name})
          | RETURNING id
        """.as[Int].head.map((_, true))
    }.map {
      case (id, created) => (GitHubRepo(id, owner, name), created)
    }

    db.run(q.transactionally)
  }
}
