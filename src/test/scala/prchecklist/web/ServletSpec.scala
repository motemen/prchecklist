package prchecklist.web

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.{ Matchers, OptionValues, mock }
import org.scalatra.test.ClientResponse
import org.scalatra.test.scalatest._
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.mockito.Matchers.{ eq => matchEq }

import prchecklist.AppServlet
import prchecklist.models._
import prchecklist.services._
import prchecklist.utils._
import prchecklist.test._

import scalaz.concurrent.Task

class ServletSpec extends ScalatraFunSuite with Matchers with OptionValues with mock.MockitoSugar
    with RepoServiceComponent with PostgresDatabaseComponent with TestAppConfig {

  override val repoService = new RepoService

  override protected def withResponse[A](res: ClientResponse)(f: => A): A = super.withResponse(res) {
    withClue(s"$body\n") { f }
  }

  val testServlet = new AppServlet {
    put("/@user") {
      session += "userLogin" -> params("login")
      session += "accessToken" -> ""
    }

    override def createGitHubService(client: GitHubHttpClient): GitHubService = {
      val service = mock[GitHubService]

      import GitHubTypes._

      when(service.getRepo("test-owner", "test-name"))
        .thenReturn(Task{ Repo("test-owner/test-name", false) })

      when(service.getRepo("motemen", "test-repository"))
        .thenReturn(Task{ Repo("motemen/test-repository", false) })

      when(service.getPullRequestWithCommits(any(), any()))
        .thenReturn(Task {
          GitHubTypes.PullRequestWithCommits(
            pullRequest = GitHubTypes.PullRequest(
              number = 2,
              title = "title",
              body = "body",
              state = "open",
              head = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "feature-1"),
              base = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "master"),
              commits = 1
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
        })

      when(service.listReleasePullRequests(any()))
        .thenReturn(
          Task {
            List(
              GitHubTypes.PullRequestRef(
                number = 100,
                title = "title",
                state = "open",
                head = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "feature-1"),
                base = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "master")
              ),
              GitHubTypes.PullRequestRef(
                number = 101,
                title = "title",
                state = "open",
                head = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "feature-2"),
                base = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "master")
              )
            )
          }
        )

      service
    }

    // FIXME: should mock methods of higher-levels (eg. services)
    override def createGitHubHttpClient(u: GitHubAccessible): GitHubHttpClient = {
      val client = mock[GitHubHttpClient]

      import GitHubTypes._

      def stubJson[A](url: String, data: A) {
        when(client.getJson[A](matchEq(url))(any(), any()))
          .thenReturn(Task { data })
      }

      val repo = Repo(fullName = "motemen/test-repository", `private` = false)

      stubJson(
        "/repos/test-owner/test-name",
        Repo(
          "test-owner/test-name", false)
      )

      stubJson(
        "/repos/motemen/test-repository/pulls/2",
        PullRequest(
          number = 2,
          title = "title",
          body = "body",
          state = "open",
          head = CommitRef(
            repo = repo,
            sha = "",
            ref = "feature-1"
          ),
          base = CommitRef(
            repo = repo,
            sha = "",
            ref = "master"
          ),
          commits = 1
        )
      )

      stubJson(
        "/repos/motemen/test-repository/pulls/2/commits?per_page=100",
        List(
          Commit(
            sha = "",
            commit = CommitDetail(
              """Merge pull request #1 from motemen/feature-1
                |
                |feature-1
              """.stripMargin
            )
          ),
          Commit(
            sha = "",
            commit = CommitDetail("Implement feature-1")
          )
        )
      )

      client
    }
  }

  addServlet(testServlet, "/*")

  import scala.concurrent.Await
  import scala.concurrent.duration.Duration
  import scala.concurrent.ExecutionContext.Implicits.global

  repoService.create(GitHubTypes.Repo("motemen/test-repository", false), "<no token>").run

  test("index") {
    get("/") {
      status should equal (200)
    }
  }

  test("viewPullRequest") {
    session {
      get("/motemen/test-repository/pull/2") {
        status should equal (200)
      }

      put("/@user?login=test-user") {
        status should equal (200)
      }

      get("/motemen/test-repository/pull/2") {
        status should equal (200)
      }
    }
  }

  test("checkFeaturePR") {
    session {
      put("/@user?login=test-user") {
        status should equal (200)
      }

      post("/motemen/test-repository/pull/2/-/check/1") {
        status should equal (302)
        header.get("Location").value should endWith ("/motemen/test-repository/pull/2")
      }
    }
  }

  test("viewRepo") {
    get("/nonexistent/nonexistent") {
      status should equal (404)
    }

    get("/motemen/test-repository") {
      status should equal (200)
    }
  }

  test("postWebhook") {
    import scala.io.Source

    val payload = Source.fromInputStream(getClass.getResourceAsStream("/webhook/pr-synchronize.json")).toArray.map(_.toByte)
    post("/webhook", payload) {
      status should equal (200)
    }
  }

  test("registerRepo") {
    session {
      put("/@user?login=test-user") {
        status should equal (200)
      }

      post("/repos", Map("owner" -> "test-owner", "name" -> "test-name")) {
        status should equal (302)
        header.get("Location").value should endWith ("/repos")
      }

      post("/repos", Map("owner" -> "test-owner", "name" -> "nonexistent-repo")) {
        status shouldNot equal (302)
      }
    }
  }

  test("listRepos") {
    session {
      put("/@user?login=test-user") { status should equal (200) }

      get("/repos") {
        status should equal (200)
        body should include ("test-owner/test-name")
      }
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
}
