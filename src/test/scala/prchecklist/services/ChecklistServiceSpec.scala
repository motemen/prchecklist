package prchecklist.services

import prchecklist.models._

import com.github.tarao.nonempty.NonEmpty

import org.scalatest._
import org.scalatest.time._

import scala.concurrent.ExecutionContext.Implicits.global

class ChecklistServiceSpec extends FunSuite with Matchers with OptionValues with concurrent.ScalaFutures {
  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(5, Millis))

  test("getChecklist && checkChecklist succeeds") {
    val checkerUser = Visitor(login = "test", accessToken = "")

    val pr =
      ReleasePullRequest(
        repo = GitHubRepo("test-owner", "test-project"),
        number = 1,
        title = "",
        body = "",
        featurePullRequests = List(
          PullRequestReference(2, "blah blah"),
          PullRequestReference(3, "foo")
        )
      )

    val fut = ChecklistService.getChecklist(pr).flatMap {
      case (checklist, created) =>
        ChecklistService.checkChecklist(
          checklist = checklist,
          checkerUser = checkerUser,
          featurePRNumber = 2
        )
    }.flatMap {
      _ => ChecklistService.getChecklist(pr)
    }

    whenReady(fut) {
      case (checklist, created) =>
        checklist.checks.get(2).value shouldBe 'checked
        checklist.checks.get(3).value shouldNot be('checked)
        checklist.checks.get(4) shouldBe 'empty
    }
  }
}
