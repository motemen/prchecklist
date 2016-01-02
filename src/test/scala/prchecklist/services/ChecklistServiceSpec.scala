package prchecklist.services

import com.github.tarao.nonempty.NonEmpty
import org.scalatest._

import prchecklist.models._

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.time._

class ChecklistServiceSpec extends FunSuite with Matchers with OptionValues with concurrent.ScalaFutures {
  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(5, Millis))

  test("checkChecklist succeeds") {
    val checklist = ReleaseChecklist(
      pullRequest = ReleasePullRequest(
        repo = GitHubRepo("test-owner", "test-project"),
        number = 1,
        title = "",
        body = "",
        featurePullRequestNumbers = List(2, 3)
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
      checklist =>
        checklist.checks.get(2).value shouldBe 'checked
        checklist.checks.get(3).value shouldNot be('checked)
        checklist.checks.get(4) shouldBe 'empty
    }
  }
}
