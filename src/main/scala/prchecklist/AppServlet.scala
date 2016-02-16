package prchecklist

import prchecklist.models._
import prchecklist.services._
import prchecklist.utils.GitHubHttpClient
import prchecklist.utils.UriStringContext._ // uri""
import prchecklist.views.Helper

import org.scalatra._
import org.scalatra.scalate.ScalateSupport

import org.json4s.jackson.JsonMethods

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import scalaz.syntax.std.option._
import scalaz.concurrent.Task

trait GitHubServiceFactory {
  self: GitHubHttpClientFactory =>

  def createGitHubService(client: GitHubHttpClient): GitHubService

  def createGitHubService(u: GitHubAccessible): GitHubService = createGitHubService(createGitHubHttpClient(u))
}

trait GitHubHttpClientFactory {
  def createGitHubHttpClient(u: GitHubAccessible): GitHubHttpClient
}

class AppServlet extends AppServletBase with GitHubServiceFactory with GitHubHttpClientFactory {
  override def createGitHubHttpClient(u: GitHubAccessible) =
    new GitHubHttpClient(u.accessToken)

  override def createGitHubService(client: GitHubHttpClient) =
    new GitHubService(client)
}

class AppServletBase extends ScalatraServlet with FutureSupport with ScalateSupport {
  self: GitHubServiceFactory with GitHubHttpClientFactory =>

  import scala.language.implicitConversions
  implicit override def string2RouteMatcher(path: String): RouteMatcher = RailsPathPatternParser(path)

  notFound {
    contentType = null
    serveStaticResource() getOrElse resourceNotFound()
  }

  before () {
    templateAttributes += "visitor" -> getVisitor
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
          redirect(uri"/auth?location=${request.uri.getPath}".toString)
        } else {
          redirect("/")
        }
    }
  }

  // TODO: Check visibility
  // params: repoOwner, repoName
  private def requireGitHubRepo(f: Repo => Any): Any = {
    Await.result(RepoService.get(params('repoOwner), params('repoName)), Duration.Inf) match {
      case Some(repo) =>
        f(repo)

      case None =>
        // TODO show repo registration form
        NotFound("Repository not found")
    }
  }

  private def requireChecklist(f: (Repo, ReleaseChecklist) => Any): Any = {
    requireGitHubRepo {
      repo =>
        // TODO: check visilibity
        val stage = params.getOrElse('stage, "")
        val client = createGitHubHttpClient(getVisitor getOrElse repo.defaultUser)
        val githubService = createGitHubService(client)
        val prWithCommits = githubService.getPullRequestWithCommits(repo, params('pullRequestNumber).toInt).run
        val (checklist, _) = Await.result(ChecklistService.getChecklist(repo, prWithCommits, stage), Duration.Inf)
        f(repo, checklist)
    }
  }

  val viewPullRequest = get("/:repoOwner/:repoName/pull/:pullRequestNumber(/:stage)") {
    requireChecklist {
      (repo, checklist) =>
        contentType = "text/html"
        layoutTemplate(
          "/WEB-INF/templates/views/pullRequest.jade",
          "checklist" -> checklist
        )
    }
  }

  private def checklistPath(checklist: ReleaseChecklist): java.net.URI = {
    new java.net.URI(Helper.checklistPath(checklist))
  }

  val checkFeaturePR = post("/:repoOwner/:repoName/pull/:pullRequestNumber(/:stage)/-/check/:featureNumber") {
    val featureNumber = params('featureNumber).toInt

    requireVisitor {
      visitor =>
        requireChecklist {
          (repo, checklist) =>
            Await.result(ChecklistService.checkChecklist(checklist, visitor, featureNumber), Duration.Inf)
            redirect(checklistPath(checklist).toString)
        }
    }
  }

  val uncheckFeaturePR = post("/:repoOwner/:repoName/pull/:pullRequestNumber(/:stage)/-/uncheck/:featureNumber") {
    val featureNumber = params('featureNumber).toInt

    requireVisitor {
      visitor =>
        requireChecklist {
          (repo, checklist) =>
            Await.result(ChecklistService.uncheckChecklist(checklist, visitor, featureNumber), Duration.Inf)
            redirect(checklistPath(checklist).toString)
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
        val githubService = createGitHubService(visitor)
        val githubRepo = githubService.getRepo(repoOwner, repoName).run
        new AsyncResult {
          val is = RepoService.create(githubRepo, visitor.accessToken).map {
            case (repo, created) =>
              redirect("/repos")
          }
        }
    }
  }

  val viewRepo = get("/:repoOwner/:repoName") {
    requireGitHubRepo {
      repo =>
        contentType = "text/html"
        val client = createGitHubHttpClient(getVisitor.getOrElse(repo.defaultUser))
        val githubService = createGitHubService(client)
        val pullRequests = githubService.listReleasePullRequests(repo).run
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
            val githubService = createGitHubService(client)
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

    val redirectUri = origin + uri"/auth/callback?location=${location}".toString
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

  get("/stylesheets/*.css") {
    contentType = null
    serveStaticResource() getOrElse resourceNotFound()
  }

}
