package prchecklist.repositories

import prchecklist.infrastructure
import prchecklist.models

import com.github.tarao.slickjdbc.interpolation.{ SQLInterpolation, CompoundParameter }
import com.github.tarao.nonempty.NonEmpty

import slick.driver.PostgresDriver.api.DBIO
import slick.driver.PostgresDriver.api.jdbcActionExtensionMethods

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait ChecklistRepositoryComponent {
  this: models.ModelsComponent
    with infrastructure.DatabaseComponent =>

  def checklistRepository: ChecklistRepository

  class ChecklistRepository extends SQLInterpolation with CompoundParameter {
    def getChecks(repo: Repo, pullRequestNumber: Int, stage: String, prRefs: NonEmpty[PullRequestReference]): Future[(Int, Map[Int,Check], Boolean)] = {
      val db = getDatabase

      val q = for {
        (checklistId, created) <- ensureChecklist(repo, pullRequestNumber, stage)
        checks <- queryChecklistChecks(checklistId, prRefs)
      } yield (checklistId, checks, created)

      db.run(q.transactionally)
    }

    def getCheckFromChecklist(checklist: ReleaseChecklist): Future[Map[Int,Check]] = {
      val db = getDatabase
      val q = for {
        checks <- queryChecklistChecks(checklist.id, NonEmpty.fromTraversable(checklist.featurePullRequests).get)
      } yield checks

      db.run(q.transactionally)
    }

    def createCheck(checklist: ReleaseChecklist, checkerUser: Visitor, featurePRNumber: Int): Future[ReleaseChecklist] = {
      val db = getDatabase

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

      db.run(q.transactionally).flatMap {
        _ => checklistRepository.getCheckFromChecklist(checklist).map {
          checks =>
            checklist.copy(checks = checks)
        }
      }
    }

    def deleteCheck(checklist: ReleaseChecklist, checkerUser: Visitor, featurePRNumber: Int): Future[Unit] = {
      val db = getDatabase

      val q = sqlu"""
        | DELETE FROM checks
        | WHERE checklist_id = ${checklist.id}
        |   AND feature_pr_number = ${featurePRNumber}
        |   AND user_login = ${checkerUser.login}
      """.map(_ => ())

      db.run(q)
    }

    private def ensureChecklist(repo: Repo, number: Int, stage: String): DBIO[(Int, Boolean)] = {
      sql"""
        | SELECT id
        | FROM checklists
        | WHERE github_repo_id = ${repo.id}
        |   AND release_pr_number = ${number}
        |   AND stage = ${stage}
        | LIMIT 1
      """.as[Int].map(_.headOption).flatMap {
        case Some(v) => DBIO.successful((v, false))
        case None =>
          sql"""
            | INSERT INTO checklists
            |   (github_repo_id, release_pr_number, stage)
            | VALUES
            |   (${repo.id}, ${number}, ${stage})
            | RETURNING id
          """.as[Int].head.map((_, true))
      }
    }

    private def queryChecklistChecks(checklistId: Int, featurePRs: NonEmpty[PullRequestReference]): DBIO[Map[Int, Check]] = {
      // TODO fix NonEmpty so that NonEmpty#map returns NonEmpty
      NonEmpty.fromTraversable(featurePRs.map(_.number)) match {
        case None =>
          DBIO.successful { Map.empty }

        case Some(featurePRNumbers) =>
          sql"""
            | SELECT feature_pr_number,user_login
            | FROM checks
            | WHERE checklist_id = ${checklistId}
            |   AND feature_pr_number IN (${featurePRNumbers})
          """.as[(Int, String)]
            .map {
              rows =>
                val prNumberToUser: Map[Int, List[User]] =
                  rows.toList.groupBy(_._1)
                    .mapValues { _.map { case (_, name) => User(name) } }
                    .withDefault { _ => List() }

                featurePRs.map {
                  pr =>
                    val checkedUsers = prNumberToUser(pr.number)
                    pr.number -> Check(pr, checkedUsers)
                }.toMap
            }
      }
    }
  }
}
