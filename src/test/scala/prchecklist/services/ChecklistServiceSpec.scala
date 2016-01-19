package prchecklist.services

import prchecklist.models._

import com.github.tarao.nonempty.NonEmpty

import org.scalatest._
import org.scalatest.time._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ChecklistServiceSpec extends FunSuite with Matchers with OptionValues with concurrent.ScalaFutures {
  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(5, Millis))

  val (repo, _) = Await.result(RepoService.create("motemen", "test-repository", "<no token>"), Duration.Inf)

  test("getChecklist && checkChecklist succeeds") {
    val checkerUser = Visitor(login = "test", accessToken = "")

    val pr =
      ReleasePullRequest(
        repo = repo,
        number = 1,
        title = "",
        body = "",
        featurePullRequests = List(
          PullRequestReference(2, "blah blah"),
          PullRequestReference(3, "foo")
        )
      )

    val fut = ChecklistService.getChecklist(repo, pr).flatMap {
      case (checklist, created) =>
        ChecklistService.checkChecklist(
          checklist = checklist,
          checkerUser = checkerUser,
          featurePRNumber = 2
        )
    }.flatMap {
      _ => ChecklistService.getChecklist(repo, pr)
    }

    whenReady(fut) {
      case (checklist, created) =>
        checklist.checks.get(2).value shouldBe 'checked
        checklist.checks.get(3).value shouldNot be('checked)
        checklist.checks.get(4) shouldBe 'empty
    }
  }
}
