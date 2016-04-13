package prchecklist

import prchecklist.models._
import prchecklist.services._
import prchecklist.utils.{ AppConfig, AppConfigFromEnv, GitHubHttpClient }
import prchecklist.utils.UriStringContext._ // uri""
import prchecklist.views.Helper

import org.scalatra._
import org.scalatra.scalate.ScalateSupport

import org.json4s.jackson.JsonMethods

import scalaz.syntax.std.option._
import scalaz.concurrent.Task

class AppServlet
    extends AppServletBase
    with GitHubServiceComponent
    with GitHubHttpClientComponent
    with GitHubAuthServiceComponent
    with GitHubConfig
    with RepoServiceComponent
    with ChecklisetServiceComponent
    with PostgresDatabaseComponent
    with RedisComponent
    with AppConfigFromEnv
    with TypesComponent {

  override def createGitHubHttpClient(u: GitHubAccessible) = new GitHubHttpClient(u.accessToken)

  override def createGitHubService(client: GitHubHttpClient): GitHubService = new GitHubService(client)

  override val repoService = new RepoService

  override val checklistService = new ChecklistService

  override val githubAuthService = new GitHubAuthService

  override val redis = new Redis
}

class AppServletBase extends ScalatraServlet with FutureSupport with ScalateSupport {
  self: GitHubServiceComponent with GitHubHttpClientComponent with RepoServiceComponent with ChecklisetServiceComponent with GitHubAuthServiceComponent with AppConfig with GitHubConfig with TypesComponent =>

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
    repoService.get(params('repoOwner), params('repoName)).run match {
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
        val (checklist, _) = checklistService.getChecklist(repo, prWithCommits, stage).run
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
            checklistService.checkChecklist(checklist, visitor, featureNumber).run
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
            checklistService.uncheckChecklist(checklist, visitor, featureNumber).run
            redirect(checklistPath(checklist).toString)
        }
    }
  }

  val listRepos = get("/repos") {
    contentType = "text/html"
    val repos = repoService.list().run
    layoutTemplate("/WEB-INF/templates/views/repos.jade", "repos" -> repos)
  }

  val registerRepo = post("/repos") {
    val repoOwner = params('owner)
    val repoName = params('name)

    requireVisitor {
      visitor =>
        val githubService = createGitHubService(visitor)
        val githubRepo = githubService.getRepo(repoOwner, repoName).run
        val (repo, created) = repoService.create(githubRepo, visitor.accessToken).run
        redirect("/repos")
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
    Found(githubAuthService.authorizationURL(redirectUri))
  }

  val authCallback = get("/auth/callback") {
    params.get("code").fold(BadRequest("code required")) {
      code =>
        val visitor = githubAuthService.authorize(code).run
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
