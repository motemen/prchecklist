package prchecklist.services

import prchecklist.models._

import com.github.tarao.slickjdbc.interpolation.{SQLInterpolation,CompoundParameter}

import slick.driver.PostgresDriver.api.DBIO
import slick.driver.PostgresDriver.api.jdbcActionExtensionMethods

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ChecklistService extends SQLInterpolation with CompoundParameter {
  def getChecklist(releasePR: ReleasePullRequest): Future[ReleaseChecklist] = {
    getChecklistChecks(releasePR).map {
      checks => ReleaseChecklist(releasePR, checks)
    }
  }

  def checkChecklist(checklist: ReleaseChecklist, checkerUser: Visitor, featurePRNumber: Int): Future[Unit] = {
    val db = Database.get

    val q = sqlu"""
      | UPDATE checks
      | SET updated_at = NOW()
      | WHERE repository_full_name = ${checklist.pullRequest.repo.fullName}
      |   AND release_pr_number = ${checklist.pullRequest.number}
      |   AND feature_pr_number = ${featurePRNumber}
      |   AND user_login = ${checkerUser.login}
    """.flatMap {
      updated =>
        if (updated == 0) {
          sqlu"""
            | INSERT INTO checks
            | (repository_full_name, release_pr_number, feature_pr_number, user_login, created_at)
            | VALUES
            | (${checklist.pullRequest.repo.fullName}, ${checklist.pullRequest.number}, ${featurePRNumber}, ${checkerUser.login}, NOW())
          """.map(_ => ())
        } else {
          DBIO.successful(())
        }
    }

    db.run(q.transactionally)
  }

  private[this] def getChecklistChecks(releasePR: ReleasePullRequest): Future[Map[Int, Check]] = {
    val db = Database.get

    db.run(
      sql"""
         | SELECT feature_pr_number,user_login
         | FROM checks
         | WHERE repository_full_name = ${releasePR.repo.fullName}
         |   AND release_pr_number = ${releasePR.number}
         |   AND feature_pr_number IN (${releasePR.featurePullRequestNumbers})
          """.as[(Int, String)]
    ).map {
      rows =>
        val prNumberToUserNames: Map[Int, List[String]]
        = rows.toList.groupBy(_._1) .mapValues { _.map(_._2) }

        releasePR.featurePullRequestNumbers.map {
          nr =>
            val checkedUsers = prNumberToUserNames.getOrElse(nr, List()).map {
              name => User(name)
            }
            (nr, Check(nr, checkedUsers))
        }.toMap
    }
  }
}
