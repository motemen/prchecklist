package prchecklist.services

import prchecklist.infrastructure._
import prchecklist.models._
import prchecklist.repositories._
import prchecklist.test._
import prchecklist.utils.RunnableFuture
import org.scalatest._
import org.scalatest.time._
import org.scalatest.mock._
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Mockito, Matchers => MockitoMatchers}
import org.mockito.Matchers._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

import scala.concurrent.{ExecutionContext, Future}

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
    with ChecklistRepositoryComponent
    {

  val githubAccessor = Visitor(login = "test", accessToken = "")

  def repoRepository = new RepoRepository

  def checklistRepository = new ChecklistRepository

  var _slackNotificationService: SlackNotificationService = null

  override def slackNotificationService = _slackNotificationService

  def slackNotificationService_=(s: SlackNotificationService): Unit = {
    _slackNotificationService = s
  }

  var projectConfigMock: Option[ProjectConfig] = None

  override def projectConfigRepository(githubRepos: GitHubRepository): ProjectConfigRepository = new ProjectConfigRepository {
    override def loadProjectConfig(repo: Repo, ref: String): Future[Option[ProjectConfig]] =
      Future.successful { projectConfigMock }

    override val github = githubRepos
  }

  override def githubRepository(githubAccessor: GitHubAccessible) = {
    val githubRepository = mock[GitHubRepository]

    when {
      githubRepository.getPullRequest(any(), any())
    } thenAnswer {
      new Answer[Future[GitHubTypes.PullRequest]] {
        override def answer(invocation: InvocationOnMock) = {
          Future.successful {
            GitHubTypes.PullRequest(
              number = invocation.getArgumentAt(1, classOf[Int]),
              title = "",
              body = "",
              state = "closed",
              head = GitHubTypes.CommitRef(GitHubTypes.Repo("", false), "xxx", "xxx"),
              base = GitHubTypes.CommitRef(GitHubTypes.Repo("", false), "xxx", "xxx"),
              commits = 1,
              assignee = None,
              user = GitHubTypes.User(login = "motemen", avatarUrl = "https://github.com/motemen.png")
            )
          }
        }
      }
    }

    githubRepository
  }

  def checklistService = new ChecklistService(githubAccessor)

  def http = new Http

  def redis = new Redis

  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(5, Millis))

  lazy val (repo, _) = repoRepository.create(GitHubTypes.Repo("motemen/test-repository", false), "<no token>").run

  private def createReleasePullRequest(number: Int, features: List[(Int, String)]) = {
    GitHubTypes.PullRequestWithCommits(
      pullRequest = Factory.createGitHubPullRequest.copy(number = number),
      commits = features.map {
        case (featureNr, name) =>
          GitHubTypes.Commit("", GitHubTypes.CommitDetail(
            s"""Merge pull request #$featureNr from motemen/$name
              |
              |$name
            """.stripMargin
          ))
      }
    )
  }

  test("getChecklist && checkChecklist succeeds") {
    val checkerUser = Visitor(login = "test", accessToken = "")

    val pr = createReleasePullRequest(99, List((2, "feature-a"), (3, "feature-b")))

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

  test("checkChecklist and notification, without events setting") {
    val checkerUser = Visitor(login = "test", accessToken = "")

    val pr = createReleasePullRequest(100, List((2, "feature-a"), (3, "feature-b")))

    projectConfigMock = Some(
      ProjectConfig(
        notification = ProjectConfig.Notification(
          events = None,
          channels = Map(
            "default" -> ProjectConfig.Channel(url = "http://test/default")
          )
        )
      )
    )

    val cap = ArgumentCaptor.forClass(classOf[String])

    slackNotificationService = mock[SlackNotificationService]

    when {
      slackNotificationService.send(any(), any())(any())
    } thenReturn {
      Future.successful { () }
    }

    {
      val (checklist, _) = checklistService.getChecklist(repo, pr, stage = "").run
      checklistService.checkChecklist(checklist, checkerUser, featurePRNumber = 2).run

      verify(slackNotificationService, Mockito.timeout(1000)).send(MockitoMatchers.eq("http://test/default"), cap.capture())(any())
      cap.getValue.asInstanceOf[String].lines should have length 1
    }

    reset(slackNotificationService)

    {
      val (checklist, _) = checklistService.getChecklist(repo, pr, stage = "").run
      checklistService.checkChecklist(checklist, checkerUser, featurePRNumber = 3).run
      verify(slackNotificationService, Mockito.timeout(1000)).send(MockitoMatchers.eq("http://test/default"), cap.capture())(any())

      cap.getValue.asInstanceOf[String].lines should have length 2 // has "all-checked" message
    }
  }

  test("checkChecklist and notification, with events setting") {
    val checkerUser = Visitor(login = "test", accessToken = "")

    val pr = createReleasePullRequest(101, List((2, "feature-a"), (3, "feature-b")))

    projectConfigMock = Some(
      ProjectConfig(
        notification = ProjectConfig.Notification(
          events = Some(Map(
            "on_check" -> List("default"),
            "on_complete" -> List("complete")
          )),
          channels = Map(
            "default" -> ProjectConfig.Channel(url = "http://test/default"),
            "complete" -> ProjectConfig.Channel(url = "http://test/complete")
          )
        )
      )
    )

    val cap = ArgumentCaptor.forClass(classOf[String])

    slackNotificationService = mock[SlackNotificationService]

    when {
      slackNotificationService.send(any(), any())(any())
    } thenReturn {
      Future.successful { () }
    }

    {
      val (checklist, _) = checklistService.getChecklist(repo, pr, stage = "").run
      checklistService.checkChecklist(checklist, checkerUser, featurePRNumber = 2).run

      verify(slackNotificationService, Mockito.timeout(1000)).send(MockitoMatchers.eq("http://test/default"), cap.capture())(any())
      cap.getValue.asInstanceOf[String].lines should have length 1
    }

    reset(slackNotificationService)

    {
      val (checklist, _) = checklistService.getChecklist(repo, pr, stage = "").run
      checklistService.checkChecklist(checklist, checkerUser, featurePRNumber = 3).run

      verify(slackNotificationService, Mockito.timeout(1000)).send(MockitoMatchers.eq("http://test/default"), cap.capture())(any())
      cap.getValue.asInstanceOf[String].lines should have length 1

      verify(slackNotificationService, Mockito.timeout(1000)).send(MockitoMatchers.eq("http://test/complete"), cap.capture())(any())
      cap.getValue.asInstanceOf[String].lines should have length 1
    }
  }
}
