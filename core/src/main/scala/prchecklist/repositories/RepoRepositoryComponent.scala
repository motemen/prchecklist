package prchecklist.repositories

import com.github.tarao.slickjdbc.interpolation.SQLInterpolation
import prchecklist.infrastructure.DatabaseComponent
import prchecklist.models.{ GitHubTypes, ModelsComponent }

import slick.dbio.DBIO
import slick.driver.PostgresDriver.api.jdbcActionExtensionMethods // q.transactionally

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait RepoRepositoryComponent {
  self: DatabaseComponent with ModelsComponent =>

  def repoRepository: RepoRepository

  class RepoRepository extends SQLInterpolation {

    def get(owner: String, name: String): Future[Option[Repo]] = {
      val db = getDatabase
      db.run(getQuery(owner, name))
    }

    def create(githubRepo: GitHubTypes.Repo, defaultAccessToken: String): Future[(Repo, Boolean)] = {
      val db = getDatabase

      val (owner, name) = (githubRepo.owner, githubRepo.name)

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
            id => (Repo(id, owner, name, defaultAccessToken), true)
          }
      }

      db.run(q.transactionally)
    }

    // TODO: visibility
    // TODO: paging
    def list(): Future[List[Repo]] = {
      val db = getDatabase

      val q = sql"""
      | SELECT id, owner, name, default_access_token FROM github_repos
    """.as[(Int, String, String, String)].map {
        _.map(Repo.tupled).toList
      }

      db.run(q)
    }

    private def getQuery(owner: String, name: String): DBIO[Option[Repo]] = {
      sql"""
      | SELECT id, default_access_token
      | FROM github_repos
      | WHERE owner = ${owner}
      |   AND name = ${name}
      | LIMIT 1
    """.as[(Int, String)].map(_.headOption.map {
        case (id, defaultAccessToken) => Repo(id, owner, name, defaultAccessToken)
      })
    }

  }
}
