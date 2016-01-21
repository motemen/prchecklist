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

class AppServletBase extends ScalatraServlet with FutureSupport with ScalateSupport with UrlGeneratorSupport {
  self: GitHubServiceFactory with GitHubHttpClientFactory =>

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

  // TODO: Check visibility
  // params: repoOwner, repoName
  private def requireGitHubRepo(f: Repo => Any): Any = {
    Await.result(RepoService.get(params('repoOwner), params('repoName)), Duration.Inf) match {
      case Some(repo) =>
        f(repo)

      case None =>
        redirect(url(listRepos))
    }
  }

  private def requireChecklist(f: (Repo, ReleaseChecklist) => Any): Any = {
    requireGitHubRepo {
      repo =>
        // TODO: check visilibity
        val client = createGitHubHttpClient(getVisitor getOrElse repo.defaultUser)
        val githubService = createGitHubService(client)
        val prWithCommits = githubService.getPullRequestWithCommits(repo, params('pullRequestNumber).toInt).run
        val (checklist, _) = Await.result(ChecklistService.getChecklist(repo, prWithCommits), Duration.Inf)
        f(repo, checklist)
    }
  }

  val viewPullRequest = get("/:repoOwner/:repoName/pull/:pullRequestNumber") {
    requireChecklist {
      (repo, checklist) =>
        contentType = "text/html"
        layoutTemplate(
          "/WEB-INF/templates/views/pullRequest.jade",
          "checklist" -> checklist
        )
    }
  }

  val checkFeaturePR = post("/:repoOwner/:repoName/pull/:pullRequestNumber/-/check/:featureNumber") {
    val featureNumber = params('featureNumber).toInt

    requireVisitor {
      visitor =>
        requireChecklist {
          (repo, checklist) =>
            Await.result(ChecklistService.checkChecklist(checklist, visitor, featureNumber), Duration.Inf)
            redirect(url(viewPullRequest, "repoOwner" -> params('repoOwner), "repoName" -> params('repoName), "pullRequestNumber" -> params('pullRequestNumber)))
        }
    }
  }

  val uncheckFeaturePR = post("/:repoOwner/:repoName/pull/:pullRequestNumber/-/uncheck/:featureNumber") {
    val featureNumber = params('featureNumber).toInt

    requireVisitor {
      visitor =>
        requireChecklist {
          (repo, checklist) =>
            Await.result(ChecklistService.uncheckChecklist(checklist, visitor, featureNumber), Duration.Inf)
            redirect(url(viewPullRequest, "repoOwner" -> params('repoOwner), "repoName" -> params('repoName), "pullRequestNumber" -> params('pullRequestNumber)))
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
              redirect(url(listRepos))
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
