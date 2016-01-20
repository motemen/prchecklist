package prchecklist.services

import prchecklist.models._

import com.github.tarao.slickjdbc.interpolation.{ SQLInterpolation, CompoundParameter }
import com.github.tarao.nonempty.NonEmpty

import slick.driver.PostgresDriver.api.DBIO
import slick.driver.PostgresDriver.api.jdbcActionExtensionMethods

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ChecklistService extends SQLInterpolation with CompoundParameter {
  private def mergedPullRequests(commits: List[GitHubTypes.Commit]): Option[NonEmpty[PullRequestReference]] = {
    NonEmpty.fromTraversable {
      commits.flatMap {
        c =>
          """^Merge pull request #(\d+) from [^\n]+\s+(.+)""".r.findFirstMatchIn(c.commit.message) map {
            m => PullRequestReference(m.group(1).toInt, m.group(2))
          }
      }
    }
  }

  def getChecklist(repo: Repo, prWithCommits: GitHubTypes.PullRequestWithCommits): Future[(ReleaseChecklist, Boolean)] = {
    val db = Database.get

    mergedPullRequests(prWithCommits.commits) match {
      case None =>
        Future.failed(new Error("No merged pull requests"))

      case Some(prRefs) =>
        val q = for {
          (checklistId, created) <- ensureChecklist(repo, prWithCommits.pullRequest.number)
          checks <- queryChecklistChecks(checklistId, prRefs)
        } yield (ReleaseChecklist(checklistId, repo, prWithCommits.pullRequest, prRefs.toList, checks), created)

        db.run(q.transactionally)
    }
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

  private def ensureChecklist(repo: Repo, number: Int): DBIO[(Int, Boolean)] = {
    sql"""
      | SELECT id
      | FROM checklists
      | WHERE github_repo_id = ${repo.id}
      |   AND release_pr_number = ${number}
      | LIMIT 1
    """.as[Int].map(_.headOption).flatMap {
      case Some(v) => DBIO.successful((v, false))
      case None =>
        sql"""
          | INSERT INTO checklists
          |   (github_repo_id, release_pr_number)
          | VALUES
          |   (${repo.id}, ${number})
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
