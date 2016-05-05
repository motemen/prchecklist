package prchecklist.services

import prchecklist.infrastructure._
import prchecklist.models._
import prchecklist.repositories._
import prchecklist.test._

import com.github.tarao.nonempty.NonEmpty

import org.scalatest._
import org.scalatest.time._
import org.scalatest.mock._
import org.mockito.Mockito._
import org.mockito.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration.Duration
import scalaz.concurrent.Task

class ChecklistServiceSpec extends FunSuite with Matchers with OptionValues with MockitoSugar with concurrent.ScalaFutures
    with WithTestDatabase
    with TestAppConfig
    with ChecklistServiceComponent
    with PostgresDatabaseComponent
    with SlackNotificationServiceComponent
    with RepoRepositoryComponent
    with GitHubRepositoryComponent
    with ProjectConfigRepositoryComponent
    with ModelsComponent
    with GitHubHttpClientComponent
    with RedisComponent
    with GitHubConfig
    {

  val githubAccessor = Visitor(login = "test", accessToken = "")

  def repoRepository = new RepoRepository

  override def githubRepository(githubAccessor: GitHubAccessible) = {
    val githubRepository = mock[GitHubRepository]

    when {
      githubRepository.getFileContent(any(), any(), any())
    } thenReturn {
      Task.fail(new Error("getFileContent: mock"))
    }

    githubRepository
  }

  def checklistService = new ChecklistService(githubAccessor)

  def http = new Http

  def redis = new Redis

  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(5, Millis))

  lazy val (repo, _) = repoRepository.create(GitHubTypes.Repo("motemen/test-repository", false), "<no token>").run

  test("getChecklist && checkChecklist succeeds") {
    val checkerUser = Visitor(login = "test", accessToken = "")

    val pr = GitHubTypes.PullRequestWithCommits(
      pullRequest = Factory.createGitHubPullRequest,
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

    {
      val (checklist, created) = checklistService.getChecklist(repo, pr, stage = "").run
      checklist.checks.get(2).value shouldNot be('checked)
      checklist.checks.get(3).value shouldNot be('checked)
      checklist.checks.get(4) shouldBe 'empty

      checklistService.checkChecklist(checklist, checkerUser, featurePRNumber = 2).run
    }

    {
      val (checklist, created) = checklistService.getChecklist(repo, pr, stage = "").run
      checklist.checks.get(2).value shouldBe 'checked
      checklist.checks.get(3).value shouldNot be('checked)
      checklist.checks.get(4) shouldBe 'empty
      created shouldBe false

      checklistService.checkChecklist(checklist, checkerUser, featurePRNumber = 3).run

    }

    {
      val (checklist, created) = checklistService.getChecklist(repo, pr, stage = "").run
      checklist.checks.get(2).value shouldBe 'checked
      checklist.checks.get(3).value shouldBe 'checked
      checklist.checks.get(4) shouldBe 'empty
      created shouldBe false
    }
  }
}
