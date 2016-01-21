package prchecklist.services

import prchecklist.models._

import com.github.tarao.nonempty.NonEmpty

import org.scalatest._
import org.scalatest.time._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration.Duration

class ChecklistServiceSpec extends FunSuite with Matchers with OptionValues with concurrent.ScalaFutures {
  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(5, Millis))

  val (repo, _) = Await.result(RepoService.create(GitHubTypes.Repo("motemen/test-repository", false), "<no token>"), Duration.Inf)

  test("getChecklist && checkChecklist succeeds") {
    val checkerUser = Visitor(login = "test", accessToken = "")

    val pr = GitHubTypes.PullRequestWithCommits(
      pullRequest = GitHubTypes.PullRequest(
        number = 1,
        title = "title",
        body = "body",
        state = "open",
        head = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "feature-1"),
        base = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "master")
      ),
      commits = List(
        GitHubTypes.Commit("", GitHubTypes.CommitDetail(
          """Merge pull request #2 from motemen/feature-a
            |
            |feature-a
          """.stripMargin
        )),
        GitHubTypes.Commit("", GitHubTypes.CommitDetail(
          """Merge pull request #3 from motemen/feature-b
            |
            |feature-b
          """.stripMargin
        ))
      )
    )

    val fut = for {
      (checklist, created) <- ChecklistService.getChecklist(repo, pr)
      _ <- Future.successful {
        checklist.checks.get(2).value shouldNot be('checked)
        checklist.checks.get(3).value shouldNot be('checked)
        checklist.checks.get(4) shouldBe 'empty
        created shouldBe true
      }

      _ <- ChecklistService.checkChecklist(checklist, checkerUser, featurePRNumber = 2)

      (checklist, created) <- ChecklistService.getChecklist(repo, pr)
      _ <- Future.successful {
        checklist.checks.get(2).value shouldBe 'checked
        checklist.checks.get(3).value shouldNot be('checked)
        checklist.checks.get(4) shouldBe 'empty
        created shouldBe false
      }

      _ <- ChecklistService.checkChecklist(checklist, checkerUser, featurePRNumber = 3)

      (checklist, created) <- ChecklistService.getChecklist(repo, pr)
      _ <- Future.successful {
        checklist.checks.get(2).value shouldBe 'checked
        checklist.checks.get(3).value shouldBe 'checked
        checklist.checks.get(4) shouldBe 'empty
        created shouldBe false
      }
    } yield ()

    fut.futureValue shouldBe (())
  }
}
