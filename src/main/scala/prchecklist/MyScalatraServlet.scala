package prchecklist

import prchecklist.models._
import prchecklist.utils.HttpUtils
import prchecklist.services._

import com.github.tarao.nonempty.NonEmpty

import org.scalatra._

import scalaz.syntax.std.option._

class MyScalatraServlet extends GithubReleasePullRequestsChecklistStack with FutureSupport {
  import HttpUtils._

  val CLIENT_ID     = System.getProperty("github.clientId").ensuring(_ != null)
  val CLIENT_SECRET = System.getProperty("github.clientSecret").ensuring(_ != null)

  implicit override def executor = scala.concurrent.ExecutionContext.Implicits.global

  get("/") {
    <html>
      <body>
        <h1>Hello, world!</h1>
        <a href="/auth">auth</a>
        <span>{session.get("userLogin")}</span>
      </body>
    </html>
  }

  get("/:owner/:project/pull/:number") {
    val owner   = params('owner)
    val project = params('project)
    val number  = params('number).toInt

    val repo = GitHubRepository(owner, project)

    val res = GitHubPullRequestService.getPullRequestFull(repo, number)
    res.attemptRun.fold(
      e => BadRequest(s"reason: $e"),
      pr =>
        new AsyncResult {
          val is =
            ChecklistService.getChecklist(pr).map {
              checklists =>
                <html>
                  <body>
                    <pre>
                      {checklists.toList}
                    </pre>
                  </body>
                </html>
            }
        }
    )
  }

  get("/auth") {
    Found(s"https://github.com/login/oauth/authorize?client_id=$CLIENT_ID")
  }

  get("/auth/callback") {
    val res = for {
      code <- params.get("code") \/> "code required"

      accessTokenResBody <- httpRequest(
        "https://github.com/login/oauth/access_token",
        _.asParamMap,
        _.postForm(Seq(
          "client_id" -> CLIENT_ID,
          "client_secret" -> CLIENT_SECRET,
          "code" -> code
        ))
      )
      accessToken <- accessTokenResBody.get("access_token") \/> s"could not get access_token $accessTokenResBody"

      user <- httpRequestJson[JsonTypes.GitHubUser]("https://api.github.com/user", _.header("Authorization", s"token $accessToken"))
    } yield {
      session += "accessToken" -> accessToken
      session += "userLogin" -> user.login

      ()
    }

    res.fold(
      msg => BadRequest(s"reason: $msg"),
      _ => Found("/")
    )
  }
}
