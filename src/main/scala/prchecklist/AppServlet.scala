package prchecklist

import prchecklist.models._
import prchecklist.services._
import prchecklist.utils.GitHubHttpClient

import org.scalatra._
import org.scalatra.scalate.ScalateSupport

import org.json4s.native.JsonMethods

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import scalaz.syntax.std.option._
import scalaz.concurrent.Task

class AppServlet extends ScalatraServlet with FutureSupport with ScalateSupport with UrlGeneratorSupport {
  notFound {
    contentType = null
    serveStaticResource() getOrElse resourceNotFound()
  }

  before () {
    templateAttributes("visitor") = getVisitor
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

  val getRoot = get("/") {
    contentType = "text/html"
    layoutTemplate("/WEB-INF/templates/views/index.jade")
  }

  private def requireVisitor(f: Visitor => Any): Any = {
    getVisitor match {
      case Some(visitor) =>
        f(visitor)

      case None =>
        if (request.requestMethod == Get) {
          redirect(url(enterAuth, Map("location" -> request.uri.getPath), Seq.empty))
        } else {
          redirect(url(getRoot))
        }
    }
  }

  private def requireGitHubRepo(f: GitHubRepo => Any): Any = {
    Await.result(GitHubRepoService.get(params('repoOwner), params('repoName)), Duration.Inf) match {
      case Some(repo) =>
        f(repo)

      case None =>
        // TODO: to the repo registration view
        redirect(url(listRepos))
    }
  }

  val viewPullRequest = get("/:repoOwner/:repoName/pull/:number") {
    val number = params('number).toInt

    getVisitor match {
      case None =>
        redirect(url(enterAuth, Map("location" -> request.uri.getPath), Seq.empty))

      case Some(visitor) =>
        requireGitHubRepo {
          repo =>
            val client = mkGitHubHttpClient(visitor)
            new GitHubPullRequestService(client).getReleasePullRequest(repo, number)
              .attemptRun.fold(
                e => BadRequest(s"error: $e"),
                pr =>
                  new AsyncResult {
                    contentType = "text/html"
                    val is =
                      ChecklistService.getChecklist(repo, pr).map {
                        case (checklist, created) =>
                          layoutTemplate(
                            "/WEB-INF/templates/views/pullRequest.jade",
                            "checklist" -> checklist
                          )
                      }
                  }
              )
        }
    }
  }

  val checkFeaturePR = post("/:repoOwner/:repoName/pull/:number/-/check/:featureNumber") {
    val number = params('number).toInt
    val featureNumber = params('featureNumber).toInt

    requireGitHubRepo {
      repo =>

        getVisitor match {
          case None => Forbidden()

          case Some(visitor) =>
            val client = mkGitHubHttpClient(visitor)
            new GitHubPullRequestService(client).getReleasePullRequest(repo, number)
              .attemptRun.fold(
                e => BadRequest(s"error: $e"), // TODO handle error
                pr =>
                  new AsyncResult {
                    val is =
                      ChecklistService.getChecklist(repo, pr).map {
                        case (checklist, created) =>
                          ChecklistService.checkChecklist(checklist, visitor, featureNumber).map {
                            _ => redirect(url(viewPullRequest, "repoOwner" -> params('repoOwner), "repoName" -> params('repoName), "number" -> number.toString))
                          }
                      }
                  }
              )
        }
    }
  }

  val uncheckFeaturePR = post("/:repoOwner/:repoName/pull/:number/-/uncheck/:featureNumber") {
    val number = params('number).toInt
    val featureNumber = params('featureNumber).toInt

    requireGitHubRepo {
      repo =>
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
                      ChecklistService.getChecklist(repo, pr).map {
                        case (checklist, created) =>
                          ChecklistService.uncheckChecklist(checklist, visitor, featureNumber).map {
                            _ => redirect(url(viewPullRequest, "repoOwner" -> params('repoOwner), "repoName" -> params('repoName), "number" -> number.toString))
                          }
                      }
                  }
              )
        }
    }
  }

  val listRepos = get("/repos") {
    new AsyncResult {
      contentType = "text/html"
      val is = GitHubRepoService.list().map {
        repos =>
          layoutTemplate("/WEB-INF/templates/views/repos.jade", "repos" -> repos)
      }
    }
  }

  val registerRepo = post("/repos") {
    val repoOwner = params('owner)
    val repoName = params('name)

    requireVisitor {
      visitor =>
        new AsyncResult {
          val is = GitHubRepoService.create(repoOwner, repoName, visitor.accessToken).map {
            case (repo, created) =>
              redirect(url(listRepos))
          }
        }
    }
  }

  val viewRepo = get("/:repoOwner/:repoName") {
    requireVisitor {
      visitor =>
        requireGitHubRepo {
          repo =>
            contentType = "text/html"
            val client = mkGitHubHttpClient(visitor)
            val pullRequests = new GitHubPullRequestService(client).listReleasePullRequests(repo).run
            layoutTemplate("/WEB-INF/templates/views/repo.jade", "repo" -> repo, "pullRequests" -> pullRequests)
        }
    }
  }

  val receiveWebhook = post("/webhook") {
    /*
    // TODO: Add comment (checklist created, checklist completed)
    // TODO: Set status (pending, success)
    JsonMethods.parse(req.body).camelizeKeys.extractOpt[GitHubWebhookPullRequestEvent].map {
      payload =>
        RepoService.get(payload.repository.fullName).map {
          repo =>
            val client = repo.makeOwnerClient()
            val githubService = new GitHubService(client)
            githubService.getChecklist(repo, payload.pullRequest, useFresh = true).map {
              case (checklist, created) =>
                if (created) {
                  githubService.addIssueComment(pr.number, s"Checklist created: ${checklist.url}")
                }
                githubService.setCommitStatus(pr.head, checklist.githubStatus)
            }
        }
    }
    // getChecklist(pr).map { case (checklist, created) =>
    //   if (created) githubService.addComment(pr, s"Created: $checklistUrl")
    // ChecklistService.invalidateCache(pr)
    // githubService.setStatus(pr.head, checklist.status)
    */
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
