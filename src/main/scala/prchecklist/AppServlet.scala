package prchecklist

import prchecklist.models._
import prchecklist.services._
import prchecklist.utils.GitHubHttpClient

import org.scalatra._
import org.scalatra.scalate.ScalateSupport

import org.json4s.jackson.JsonMethods

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

  def mkGitHubHttpClient(u: GitHubAccessible): GitHubHttpClient = {
    new GitHubHttpClient(u.accessToken)
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

  private def mkGitHubService(u: GitHubAccessible): GitHubService = {
    new GitHubService(mkGitHubHttpClient(u))
  }

  // TODO: Check visibility
  private def requireGitHubRepo(f: Repo => Any): Any = {
    Await.result(RepoService.get(params('repoOwner), params('repoName)), Duration.Inf) match {
      case Some(repo) =>
        f(repo)

      case None =>
        redirect(url(listRepos))
    }
  }

  val viewPullRequest = get("/:repoOwner/:repoName/pull/:number") {
    val number = params('number).toInt

    requireVisitor {
      visitor =>
        requireGitHubRepo {
          repo =>
            val client = mkGitHubHttpClient(visitor)
            val pr = new GitHubPullRequestService(client).getReleasePullRequest(repo, number).run
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
        }
    }
  }

  val checkFeaturePR = post("/:repoOwner/:repoName/pull/:number/-/check/:featureNumber") {
    val number = params('number).toInt
    val featureNumber = params('featureNumber).toInt

    requireVisitor {
      visitor =>
        requireGitHubRepo {
          repo =>
            val client = mkGitHubHttpClient(visitor)
            val pr = new GitHubPullRequestService(client).getReleasePullRequest(repo, number).run
            new AsyncResult {
              val is =
                ChecklistService.getChecklist(repo, pr).map {
                  case (checklist, created) =>
                    ChecklistService.checkChecklist(checklist, visitor, featureNumber).map {
                      _ => redirect(url(viewPullRequest, "repoOwner" -> params('repoOwner), "repoName" -> params('repoName), "number" -> number.toString))
                    }
                }
            }
        }
    }
  }

  val uncheckFeaturePR = post("/:repoOwner/:repoName/pull/:number/-/uncheck/:featureNumber") {
    val number = params('number).toInt
    val featureNumber = params('featureNumber).toInt

    requireVisitor {
      visitor =>
        requireGitHubRepo {
          repo =>
            val client = mkGitHubHttpClient(visitor)
            val pr = new GitHubPullRequestService(client).getReleasePullRequest(repo, number).run
            new AsyncResult {
              val is =
                ChecklistService.getChecklist(repo, pr).map {
                  case (checklist, created) =>
                    ChecklistService.uncheckChecklist(checklist, visitor, featureNumber).map {
                      _ => redirect(url(viewPullRequest, "repoOwner" -> params('repoOwner), "repoName" -> params('repoName), "number" -> number.toString))
                    }
                }
            }
        }
    }
  }

  val listRepos = get("/repos") {
    new AsyncResult {
      contentType = "text/html"
      val is = RepoService.list().map {
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
        val githubService = mkGitHubService(visitor)
        val githubRepo = githubService.getRepo(repoOwner, repoName).run
        new AsyncResult {
          val is = RepoService.create(githubRepo, visitor.accessToken).map {
            case (repo, created) =>
              redirect(url(listRepos))
          }
        }
    }
  }

  val viewRepo = get("/:repoOwner/:repoName") {
    requireGitHubRepo {
      repo =>
        contentType = "text/html"
        val client = mkGitHubHttpClient(getVisitor.getOrElse(repo.defaultUser))
        val pullRequests = new GitHubPullRequestService(client).listReleasePullRequests(repo).run
        layoutTemplate("/WEB-INF/templates/views/repo.jade", "repo" -> repo, "pullRequests" -> pullRequests)
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
    params.get("code").fold(BadRequest("code required")) {
      code =>
        val visitor = GitHubAuthService.authorize(code).run
        session += "accessToken" -> visitor.accessToken
        session += "userLogin" -> visitor.login
        Found(request.parameters.get("location").filter(_.startsWith("/")) getOrElse "/")
    }
  }
}
