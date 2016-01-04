package prchecklist

import prchecklist.models._
import prchecklist.utils.HttpUtils._
import prchecklist.services._

import org.scalatra._
import org.scalatra.scalate.ScalateSupport

import org.json4s
import org.json4s.native.Serialization

import scalaz.syntax.std.option._

import scala.concurrent.Future

import java.net.URLEncoder

class MyScalatraServlet extends GithubReleasePullRequestsChecklistStack with FutureSupport with ScalateSupport {
  val CLIENT_ID = System.getProperty("github.clientId").ensuring(_ != null, "github.clientId must be defined")
  val CLIENT_SECRET = System.getProperty("github.clientSecret").ensuring(_ != null, "github.clientSecret must be defined")

  implicit override def executor = scala.concurrent.ExecutionContext.Implicits.global

  /*
  def withChecklist[T](visitor: Visitor, repo: GitHubRepo, number: Int)(f: Future[ReleaseChecklist] => Any): Any = {
    new GitHubPullRequestService(visitor)
      .getReleasePullRequest(repo, number)
      .attemptRun.fold(
        e => BadRequest(s"reason: $e"),
        pr => ChecklistService.getChecklist(pr).map(f)
      )
  }
  */

  def getVisitor: Option[Visitor] = {
    for {
      login <- session.get("userLogin")
      accessToken <- session.get("accessToken")
    } yield {
      Visitor(login = login.asInstanceOf[String], accessToken = accessToken.asInstanceOf[String])
    }
  }

  get("/") {
    <html>
      <body>
        <h1>Hello, world!??xe</h1>
        <a href="/auth">auth</a>
        <span>{ session.get("userLogin") }</span>
      </body>
    </html>
  }

  /*
  // POST /example/webapp/pull/456/checks/@me/123 {"checked":true}
  post("/api/:owner/:repoName/pull/:number/checks/@me/:featureNumber") {
    val owner = params('owner)
    val repoName = params('repoName)
    val number = params('number).toInt
    val featureNumber = params('featureNumber).toInt

    val repo = GitHubRepo(owner, repoName)

    getVisitor match {
      case None => Forbidden()

      case Some(visitor) =>
        withChecklist(visitor, repo, number) {
          checklist =>
            ChecklistService.checkChecklist(
              checklist, visitor, featureNumber
            )
            new AsyncResult {
              val is = ???
            }
        }
    }
  }
  */

  get("/:owner/:repoName/pull/:number") {
    val owner = params('owner)
    val repoName = params('repoName)
    val number = params('number).toInt

    val repo = GitHubRepo(owner, repoName)

    getVisitor match {
      case None => redirect(s"/auth?location=${request.uri.getPath}")
      case Some(visitor) =>
        new GitHubPullRequestService(visitor).getReleasePullRequest(repo, number)
          .attemptRun.fold(
            e => BadRequest(s"error: $e"),
            pr =>
              new AsyncResult {
                contentType = "text/html"
                val is =
                  ChecklistService.getChecklist(pr).map {
                    checklist =>
                      jade("/pullRequest", "checklist" -> checklist)
                  }
              }
          )
    }
  }

  post("/:owner/:repoName/pull/:number/check/:featureNumber") {
    val owner = params('owner)
    val repoName = params('repoName)
    val number = params('number).toInt
    val featureNumber = params('featureNumber).toInt

    val repo = GitHubRepo(owner, repoName)

    getVisitor match {
      case None => Forbidden()

      case Some(visitor) =>
        new GitHubPullRequestService(visitor).getReleasePullRequest(repo, number)
          .attemptRun.fold(
            e => BadRequest(s"error: $e"),
            pr =>
              new AsyncResult {
                val is =
                  ChecklistService.getChecklist(pr).map {
                    checklist =>
                      ChecklistService.checkChecklist(checklist, visitor, featureNumber).map {
                        _ => redirect(s"/$owner/$repoName/pull/$number")
                      }
                  }
              }
          )
    }
  }

  /*
  get("/api/:owner/:repoName/pull/:number") {
    val owner = params('owner)
    val repoName = params('repoName)
    val number = params('number).toInt

    val repo = GitHubRepo(owner, repoName)
    getVisitor match {
      case None => redirect(s"/auth?location=${request.uri.getPath}")
      case Some(visitor) =>
        withChecklist(visitor, repo, number) {
          checklist =>
            new AsyncResult {
              implicit val formats = Serialization.formats(json4s.NoTypeHints)
              contentType = "application/json; charset=utf-8"
              val is = Future.successful { Serialization.write(checklist) }
            }
        }
    }
  }
  */

  get("/auth") {
    val redirectURI = s"http://localhost:8080/auth/callback?location=${request.parameters.getOrElse("location", "/")}"
    Found(s"https://github.com/login/oauth/authorize?client_id=$CLIENT_ID&redirect_uri=${URLEncoder.encode(redirectURI, "UTF-8")}")
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
      _ => Found(request.parameters.getOrElse("location", "/"))
    )
  }
}
