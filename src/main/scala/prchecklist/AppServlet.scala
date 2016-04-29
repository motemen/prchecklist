package prchecklist

import prchecklist.models._
import prchecklist.services._
import prchecklist.utils.{ HttpComponent, AppConfig, AppConfigFromEnv }
import prchecklist.utils.UriStringContext._ // uri""
import prchecklist.views.Helper

import org.scalatra._
import org.scalatra.scalate.ScalateSupport

import org.json4s.jackson.JsonMethods

import scalaz.syntax.std.option._
import scalaz.concurrent.Task

trait domain
  extends GitHubServiceComponent
  with GitHubHttpClientComponent
  with GitHubAuthServiceComponent
  with RepoServiceComponent
  with ChecklisetServiceComponent
  with PostgresDatabaseComponent
  with RedisComponent
  with TypesComponent
  with HttpComponent
  with GitHubConfig

object domain extends domain with AppConfigFromEnv {
  override val repoService = new RepoService

  override val checklistService = new ChecklistService

  override val githubAuthService = new GitHubAuthService

  override val redis = new Redis

  override val http = new Http
}

class AppServlet extends ScalatraServlet with FutureSupport with ScalateSupport {
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

  def getVisitor: Option[domain.Visitor] = {
    for {
      login <- session.get("userLogin")
      accessToken <- session.get("accessToken")
    } yield {
      domain.Visitor(login = login.asInstanceOf[String], accessToken = accessToken.asInstanceOf[String])
    }
  }

  val getRoot = get("/") {
    contentType = "text/html"
    layoutTemplate("/WEB-INF/templates/views/index.jade")
  }

  private def requireVisitor(f: domain.Visitor => Any): Any = {
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
  private def requireGitHubRepo(f: domain.Repo => Any): Any = {
    domain.repoService.get(params('repoOwner), params('repoName)).run match {
      case Some(repo) =>
        f(repo)

      case None =>
        // TODO show repo registration form
        NotFound("Repository not found")
    }
  }

  private def requireChecklist(f: (domain.Repo, domain.ReleaseChecklist) => Any): Any = {
    requireGitHubRepo {
      repo =>
        // TODO: check visilibity
        val stage = params.getOrElse('stage, "")
        val prWithCommits = new domain.GitHubService {
          override def githubAccessor = getVisitor getOrElse repo.defaultUser
        }.getPullRequestWithCommits(repo, params('pullRequestNumber).toInt).run
        val (checklist, _) = domain.checklistService.getChecklist(repo, prWithCommits, stage).run
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

  private def checklistPath(checklist: domain.ReleaseChecklist, featureNumber: Int): java.net.URI = {
    new java.net.URI(f"${Helper.checklistPath(checklist)}%s#feature-$featureNumber%d")
  }

  val checkFeaturePR = post("/:repoOwner/:repoName/pull/:pullRequestNumber(/:stage)/-/check/:featureNumber") {
    val featureNumber = params('featureNumber).toInt

    requireVisitor {
      visitor =>
        requireChecklist {
          (repo, checklist) =>
            domain.checklistService.checkChecklist(checklist, visitor, featureNumber).run
            redirect(checklistPath(checklist, featureNumber).toString)
        }
    }
  }

  val uncheckFeaturePR = post("/:repoOwner/:repoName/pull/:pullRequestNumber(/:stage)/-/uncheck/:featureNumber") {
    val featureNumber = params('featureNumber).toInt

    requireVisitor {
      visitor =>
        requireChecklist {
          (repo, checklist) =>
            domain.checklistService.uncheckChecklist(checklist, visitor, featureNumber).run
            redirect(checklistPath(checklist, featureNumber).toString)
        }
    }
  }

  val listRepos = get("/repos") {
    contentType = "text/html"
    val repos = domain.repoService.list().run
    layoutTemplate("/WEB-INF/templates/views/repos.jade", "repos" -> repos)
  }

  val registerRepo = post("/repos") {
    val repoOwner = params('owner)
    val repoName = params('name)

    requireVisitor {
      visitor =>
        val githubRepo = new domain.GitHubService {
          override def githubAccessor = visitor
        }.getRepo(repoOwner, repoName).run
        val (repo, created) = domain.repoService.create(githubRepo, visitor.accessToken).run
        redirect("/repos")
    }
  }

  val viewRepo = get("/:repoOwner/:repoName") {
    requireGitHubRepo {
      repo =>
        contentType = "text/html"
        val pullRequests = new domain.GitHubService {
          override def githubAccessor = getVisitor getOrElse repo.defaultUser
        }.listReleasePullRequests(repo).run
        layoutTemplate("/WEB-INF/templates/views/repo.jade", "repo" -> repo, "pullRequests" -> pullRequests)
    }
  }

  val receiveWebhook = post("/webhook") {
    /*
    // TODO: Add comment (checklist created, checklist completed)
    // TODO: Set status (pending, success)
    JsonMethods.parse(req.body).camelizeKeys.extractOpt[GitHubWebhookPullRequestEvent].map {
      payload =>
        repoService.get(payload.repository.fullName).map {
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
    // checklistService.invalidateCache(pr)
    // githubService.setStatus(pr.head, checklist.status)
    */
    "OK"
  }

  val enterAuth = get("/auth") {
    val scheme = request.headers.getOrElse("X-Forwarded-Proto", "http")
    val origin = new java.net.URI(scheme, request.uri.getAuthority, null, null, null)
    val location = request.parameters.getOrElse("location", "/")

    val redirectUri = origin + uri"/auth/callback?location=${location}".toString
    Found(domain.githubAuthService.authorizationURL(redirectUri))
  }

  val authCallback = get("/auth/callback") {
    params.get("code").fold(BadRequest("code required")) {
      code =>
        val visitor = domain.githubAuthService.authorize(code).run
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
