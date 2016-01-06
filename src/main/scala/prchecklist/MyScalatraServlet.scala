package prchecklist

import prchecklist.models._
import prchecklist.services._

import org.scalatra._
import org.scalatra.scalate.ScalateSupport

import org.json4s
import org.json4s.native.Serialization

import scalaz.syntax.std.option._
import scalaz.concurrent.Task

import java.net.URLEncoder

class MyScalatraServlet extends GithubReleasePullRequestsChecklistStack with FutureSupport with ScalateSupport {
  implicit override def executor = scala.concurrent.ExecutionContext.Implicits.global

  override def isScalateErrorPageEnabled = false

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
        <span>{ getVisitor }</span>
      </body>
    </html>
  }

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

  get("/auth") {
    Found(GitHubAuthService.authorizationURL(s"http://localhost:8080/auth/callback?location=${request.parameters.getOrElse("location", "/")}"))
  }

  get("/auth/callback") {
    val res = for {
      code <- Task.fromDisjunction { params.get("code") \/> new Error("code required") }
      visitor <- GitHubAuthService.authorize(code)
    } yield {
      // TODO
      session += "accessToken" -> visitor.accessToken
      session += "userLogin" -> visitor.login

      ()
    }

    res.attemptRun.fold(
      e => BadRequest(s"reason: $e"),
      _ => Found(request.parameters.getOrElse("location", "/"))
    )
  }
}
