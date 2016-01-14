package prchecklist

import prchecklist.models._
import prchecklist.services._
import prchecklist.utils.{ GitHubHttpClient, HttpUtils }

import org.scalatra._
import org.scalatra.scalate.{ ScalateUrlGeneratorSupport, ScalateSupport }

import scalaz.syntax.std.option._
import scalaz.concurrent.Task

class AppServlet extends ScalatraServlet with FutureSupport with ScalateSupport with UrlGeneratorSupport {

  notFound {
    contentType = null
    serveStaticResource() getOrElse resourceNotFound()
  }

  def mkGitHubHttpClient(visitor: Visitor): GitHubHttpClient = {
    new GitHubHttpClient(visitor.accessToken)
  }

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
    contentType = "text/html"
    layoutTemplate("/WEB-INF/templates/views/index.jade", "visitor" -> getVisitor)
  }

  val viewPullRequest = get("/:owner/:repoName/pull/:number") {
    val owner = params('owner)
    val repoName = params('repoName)
    val number = params('number).toInt

    val repo = GitHubRepo(owner, repoName)

    getVisitor match {
      case None => redirect(url(enterAuth, Map("location" -> request.uri.getPath), Seq.empty))
      case Some(visitor) =>
        val client = mkGitHubHttpClient(visitor)
        new GitHubPullRequestService(client).getReleasePullRequest(repo, number)
          .attemptRun.fold(
            e => BadRequest(s"error: $e"),
            pr =>
              new AsyncResult {
                contentType = "text/html"
                val is =
                  ChecklistService.getChecklist(pr).map {
                    checklist =>
                      layoutTemplate(
                        "/WEB-INF/templates/views/pullRequest.jade",
                        "checklist" -> checklist,
                        "visitor" -> visitor
                      )
                  }
              }
          )
    }
  }

  val checkFeaturePR = post("/:owner/:repoName/pull/:number/-/check/:featureNumber") {
    val owner = params('owner)
    val repoName = params('repoName)
    val number = params('number).toInt
    val featureNumber = params('featureNumber).toInt

    val repo = GitHubRepo(owner, repoName)

    getVisitor match {
      case None => Forbidden()

      case Some(visitor) =>
        val client = mkGitHubHttpClient(visitor)
        new GitHubPullRequestService(client).getReleasePullRequest(repo, number)
          .attemptRun.fold(
            e => BadRequest(s"error: $e"),
            pr =>
              new AsyncResult {
                val is =
                  ChecklistService.getChecklist(pr).map {
                    checklist =>
                      ChecklistService.checkChecklist(checklist, visitor, featureNumber).map {
                        _ => redirect(url(viewPullRequest, "owner" -> owner, "repoName" -> repoName, "number" -> number.toString))
                      }
                  }
              }
          )
    }
  }

  val uncheckFeaturePR = post("/:owner/:repoName/pull/:number/-/uncheck/:featureNumber") {
    val owner = params('owner)
    val repoName = params('repoName)
    val number = params('number).toInt
    val featureNumber = params('featureNumber).toInt

    val repo = GitHubRepo(owner, repoName)

    getVisitor match {
      case None => Forbidden()

      case Some(visitor) =>
        val client = mkGitHubHttpClient(visitor)
        new GitHubPullRequestService(client).getReleasePullRequest(repo, number)
          .attemptRun.fold(
            e => BadRequest(s"error: $e"),
            pr =>
              new AsyncResult {
                val is =
                  ChecklistService.getChecklist(pr).map {
                    checklist =>
                      ChecklistService.uncheckChecklist(checklist, visitor, featureNumber).map {
                        _ => redirect(url(viewPullRequest, "owner" -> owner, "repoName" -> repoName, "number" -> number.toString))
                      }
                  }
              }
          )
    }
  }

  val receiveWebhook = post("/webhook") {
    // TODO: Add comment (checklist created, checklist completed)
    // TODO: Set status (pending, success)
    "OK"
  }

  val enterAuth = get("/auth") {
    val scheme = request.headers.getOrElse("X-Forwarded-Proto", "http")
    val origin = new java.net.URI(scheme, request.uri.getAuthority, null, null, null)
    val location = request.parameters.getOrElse("location", "/")

    val redirectUri = origin + url(authCallback, Map("location" -> location), Seq.empty)
    Found(GitHubAuthService.authorizationURL(redirectUri))
  }

  val authCallback = get("/auth/callback") {
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
