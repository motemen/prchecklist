package prchecklist.web

import org.mockito.Matchers.{ eq => matchEq, _ }
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.{ Matchers, OptionValues, mock }
import org.scalatra.test.ClientResponse
import org.scalatra.test.scalatest._
import prchecklist.infrastructure.PostgresDatabaseComponent
import prchecklist.models._
import prchecklist.services._
import prchecklist.test._
import prchecklist.{ AppServletBase, Domain }
import prchecklist.utils.RunnableFuture

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ServletSpec extends ScalatraFunSuite with Matchers with OptionValues with mock.MockitoSugar with WithTestDatabase {
  override protected def withResponse[A](res: ClientResponse)(f: => A): A = super.withResponse(res) {
    withClue(s"$body\n") { f }
  }

  object TestDomain extends Domain with TestAppConfig with PostgresDatabaseComponent {
    override val repoRepository = new RepoRepository

    override val checklistRepository = new ChecklistRepository

    override val githubAuthService = new GitHubAuthService

    override val redis = new Redis

    override val http = new Http

    override def githubRepository(accessible: GitHubAccessible): GitHubRepository = {
      val repository = mock[GitHubRepository]

      when(repository.getRepo("test-owner", "test-name"))
        .thenReturn(Future{ GitHubTypes.Repo("test-owner/test-name", false) })

      when(repository.getRepo("motemen", "test-repository"))
        .thenReturn(Future{ GitHubTypes.Repo("motemen/test-repository", false) })

      when {
        repository.getPullRequestWithCommits(any(), any())
      } thenReturn {
        Future {
          GitHubTypes.PullRequestWithCommits(
            pullRequest = GitHubTypes.PullRequest(
              number = 2,
              title = "title",
              body = "body",
              state = "open",
              head = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "feature-1"),
              base = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "master"),
              commits = 1,
              assignee = None,
              user = GitHubTypes.User(login = "motemen", avatarUrl = "https://github.com/motemen.png")
            ),
            commits = List(
              GitHubTypes.Commit("", GitHubTypes.CommitDetail(
                """Merge pull request #1 from motemen/feature-a
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
        }
      }

      when {
        repository.listReleasePullRequests(any())
      } thenReturn {
        Future {
          List(
            GitHubTypes.PullRequestRef(
              number = 100,
              title = "title",
              state = "open",
              head = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "feature-1"),
              base = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "master"),
              updatedAt = new java.util.Date()
            ),
            GitHubTypes.PullRequestRef(
              number = 101,
              title = "title",
              state = "open",
              head = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "feature-2"),
              base = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "master"),
              updatedAt = new java.util.Date()
            )
          )
        }
      }

      when {
        repository.getFileContent(any(), any(), any())
      } thenReturn {
        Future.failed(new Exception("getFileContent: mock"))
      }

      when {
        repository.getPullRequest(any(), any())
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

      repository
    }

    override def githubHttpClient(accessToken: String): GitHubHttpClient = {
      val client = mock[GitHubHttpClient]

      def stubJson[A](url: String, data: A) {
        when(client.getJson[A](matchEq(url))(any(), any(), any()))
          .thenReturn(Future { data })
      }

      val repo = GitHubTypes.Repo(fullName = "motemen/test-repository", `private` = false)

      stubJson(
        "/repos/test-owner/test-name",
        GitHubTypes.Repo(
          "test-owner/test-name", false)
      )

      stubJson(
        "/repos/motemen/test-repository/pulls/2",
        GitHubTypes.PullRequest(
          number = 2,
          title = "title",
          body = "body",
          state = "open",
          head = GitHubTypes.CommitRef(
            repo = repo,
            sha = "",
            ref = "feature-1"
          ),
          base = GitHubTypes.CommitRef(
            repo = repo,
            sha = "",
            ref = "master"
          ),
          commits = 1,
          assignee = None,
          user = GitHubTypes.User(login = "motemen", avatarUrl = "https://github.com/motemen.png")
        )
      )

      stubJson(
        "/repos/motemen/test-repository/pulls/2/commits?per_page=100",
        List(
          GitHubTypes.Commit(
            sha = "",
            commit = GitHubTypes.CommitDetail(
              """Merge pull request #1 from motemen/feature-1
                |
                |feature-1
              """.stripMargin
            )
          ),
          GitHubTypes.Commit(
            sha = "",
            commit = GitHubTypes.CommitDetail("Implement feature-1")
          )
        )
      )

      client
    }
  }

  val testServlet = new AppServletBase {
    put("/@user") {
      session += "userLogin" -> params("login")
      session += "accessToken" -> ""
    }

    override val domain = TestDomain
  }

  addServlet(testServlet, "/*")

  override def beforeAll(): Unit = {
    super.beforeAll()
    TestDomain.repoRepository.create(GitHubTypes.Repo("motemen/test-repository", false), "<no token>").run
  }

  test("index") {
    get("/") {
      status should equal (302)
    }
  }

  test("authCallback") {
    session {
      get("/auth/callback") {
        status should equal (400)
      }

      // TODO: mock GitHubAuthService
      /*
      get("/auth/callback?code=cafebabe&location=/repos") {
        status should equal (302)
        header.get("Location").value should equal ("/repos")
      }

      get("/auth/callback?code=cafebabe&location=http://www.example.com/") {
        status should equal (302)
        header.get("Location").value should equal ("/")
      }
      */
    }
  }

  test("json: checklist") {
    session {
      put("/@user?login=test-user") {
        status should equal (200)
      }

      get("/-/checklist?repoOwner=motemen&repoName=test-repository&pullRequestNumber=2") {
        status should equal (200)
      }

      post("/-/checklist/check?repoOwner=motemen&repoName=test-repository&pullRequestNumber=2&featureNumber=1") {
        status should equal (200)
      }
    }
  }
}
