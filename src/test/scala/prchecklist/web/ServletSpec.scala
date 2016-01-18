package prchecklist.web

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

import scalaz.\/-
import scalaz.concurrent.Task

class ServletSpec extends ScalatraFunSuite with Matchers with OptionValues with mock.MockitoSugar {
  override protected def withResponse[A](res: ClientResponse)(f: => A): A = super.withResponse(res) {
    withClue(s"$body\n") { f }
  }

  val testServlet = new AppServlet {
    put("/@user") {
      session += "userLogin" -> params("login")
      session += "accessToken" -> ""
    }

    override def mkGitHubHttpClient(visitor: Visitor): GitHubHttpClient = {
      val client = mock[GitHubHttpClient]

      import JsonTypes._

      def stubJson[A](url: String, data: A) {
        when(client.getJson[A](matchEq(url))(any(), any()))
          .thenReturn(Task { data })
      }

      val repo = GitHubRepo(
        fullName = "motemen/test-repository",
        `private` = false,
        url = "<url>"
      )
      stubJson(
        "/repos/motemen/test-repository/pulls/2",
        GitHubPullRequest(
          number = 1,
          url = "url",
          title = "title",
          body = "body",
          head = GitHubCommitRef(
            repo = repo,
            sha = "",
            ref = "feature-1"
          ),
          base = GitHubCommitRef(
            repo = repo,
            sha = "",
            ref = "master"
          )
        )
      )

      stubJson(
        "/repos/motemen/test-repository/pulls/2/commits?per_page=100",
        List(
          GitHubCommit(
            sha = "",
            commit = GitHubCommitDetail(
              """Merge pull request #1 from motemen/feature-1
                |
                |feature-1
              """.stripMargin
            )
          ),
          GitHubCommit(
            sha = "",
            commit = GitHubCommitDetail("Implement feature-1")
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

  Await.result(GitHubRepoService.create("motemen", "test-repository"), Duration.Inf)

  test("index") {
    get("/") {
      status should equal (200)
    }
  }

  test("viewPullRequest") {
    session {
      get("/motemen/test-repository/pull/2") {
        status should equal (302)
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
      put("/@user?login=test-uesr") {
        status should equal (200)
      }

      post("/motemen/test-repository/pull/2/-/check/1") {
        status should equal (302)
        header.get("Location").value should endWith ("/motemen/test-repository/pull/2")
      }
    }
  }

  test("postWebhook") {
    import scala.io.Source

    val payload = Source.fromInputStream(getClass.getResourceAsStream("/webhook/pr-synchronize.json")).toArray.map(_.toByte)
    post("/webhook", payload) {
      status should equal (200)
    }
  }
}
