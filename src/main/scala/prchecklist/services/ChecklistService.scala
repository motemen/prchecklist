package prchecklist.services

import com.github.tarao.nonempty.NonEmpty
import prchecklist.models._

import com.github.tarao.slickjdbc.interpolation.{ SQLInterpolation, CompoundParameter }
import slick.dbio.{ Effect, NoStream, DBIOAction }

import slick.driver.PostgresDriver.api.DBIO
import slick.driver.PostgresDriver.api.jdbcActionExtensionMethods

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ChecklistService extends SQLInterpolation with CompoundParameter {
  def getChecklist(releasePR: ReleasePullRequest): Future[(ReleaseChecklist, Boolean)] = {
    val db = Database.get

    val q = for {
      (id, created) <- ensureChecklist(releasePR)
      checks <- queryChecklistChecks(id, releasePR)
    } yield (ReleaseChecklist(id, releasePR, checks), created)

    db.run(q.transactionally)
  }

  def checkChecklist(checklist: ReleaseChecklist, checkerUser: Visitor, featurePRNumber: Int): Future[Unit] = {
    val db = Database.get

    val q = sqlu"""
      | UPDATE checks
      | SET updated_at = NOW()
      | WHERE checklist_id = ${checklist.id}
      |   AND feature_pr_number = ${featurePRNumber}
      |   AND user_login = ${checkerUser.login}
    """.flatMap {
      updated =>
        if (updated == 0) {
          sqlu"""
            | INSERT INTO checks
            | (checklist_id, feature_pr_number, user_login, created_at)
            | VALUES
            | (${checklist.id}, ${featurePRNumber}, ${checkerUser.login}, NOW())
          """.map(_ => ())
        } else {
          DBIO.successful(())
        }
    }

    db.run(q.transactionally)
  }

  def uncheckChecklist(checklist: ReleaseChecklist, checkerUser: Visitor, featurePRNumber: Int): Future[Unit] = {
    val db = Database.get

    val q = sqlu"""
      | DELETE FROM checks
      | WHERE checklist_id = ${checklist.id}
      |   AND feature_pr_number = ${featurePRNumber}
      |   AND user_login = ${checkerUser.login}
    """.map(_ => ())

    db.run(q)
  }

  private def ensureChecklist(releasePR: ReleasePullRequest) = {
    sql"""
      | SELECT id
      | FROM checklist
      | WHERE repository_full_name = ${releasePR.repo.fullName}
      |   AND release_pr_number = ${releasePR.number}
      | LIMIT 1
    """.as[Int].map(_.headOption).flatMap {
      case Some(v) => DBIO.successful((v, false))
      case None =>
        sql"""
          | INSERT INTO checklist
          |   (repository_full_name, release_pr_number)
          | VALUES
          |   (${releasePR.repo.fullName}, ${releasePR.number})
          | RETURNING id
        """.as[Int].head.map((_, true))
    }
  }

  private def queryChecklistChecks(id: Int, releasePR: ReleasePullRequest) = {
    NonEmpty.fromTraversable(releasePR.featurePullRequests.map(_.number)) match {
      case None =>
        DBIO.successful(Map.empty[Int, Check])

      case Some(featurePRNrs) =>
        sql"""
          | SELECT feature_pr_number,user_login
          | FROM checks
          | WHERE id = ${id}
          |   AND feature_pr_number IN (${featurePRNrs})
        """.as[(Int, String)]
          .map {
            rows =>
              val prNumberToUserNames: Map[Int, List[String]] =
                rows.toList.groupBy(_._1).mapValues { _.map(_._2) }

              releasePR.featurePullRequests.map {
                pr =>
                  val checkedUsers =
                    prNumberToUserNames.getOrElse(pr.number, List()).map {
                      name => User(name)
                    }
                  (pr.number, Check(pr, checkedUsers))
              }.toMap
          }
    }
  }
}
