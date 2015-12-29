package prchecklist.services

import org.scalatest._

import prchecklist.models._

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.time._

class ChecklistServiceSpec extends FunSuite with Matchers with OptionValues with concurrent.ScalaFutures {
  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(5, Millis))

  val emptyPullRequest = JsonTypes.GitHubPullRequest(number = 0, url = "", title = "", body = "")

  test("checkChecklist succeeds") {
    val checklist = Checklist(
      pullRequest = GitHubPullRequestFull(
        repository = GitHubRepository("test-owner", "test-project"),
        detail = emptyPullRequest.copy(number = 1),
        commits = List(
          JsonTypes.GitHubCommit(
            sha = "",
            commit = JsonTypes.GitHubCommitDetail("Merge pull request #2 from ...")
          ),
          JsonTypes.GitHubCommit(
            sha = "",
            commit = JsonTypes.GitHubCommitDetail("Merge pull request #3 from ...")
          )
        )
      ),
      checks = Map.empty
    )
    val checkerUser = Visitor(login = "test", accessToken = "")

    val fut = ChecklistService.checkChecklist(
      checklist = checklist,
      checkerUser = checkerUser,
      featurePRNumber = 2
    ).flatMap {
      _ => ChecklistService.getChecklist(checklist.pullRequest)
    }

    whenReady(fut) {
      checklistOption =>
        checklistOption.flatMap(_.checks.get(2)).value shouldBe 'checked
        checklistOption.flatMap(_.checks.get(3)).value shouldNot be ('checked)
        checklistOption.flatMap(_.checks.get(4)) shouldBe 'empty
    }
  }
}
