package prchecklist

import org.scalatra._
import org.json4s
import org.json4s.native.{ Serialization => JsonSerialization }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import prchecklist.infrastructure.{ DatabaseComponent, GitHubHttpClientComponent, PostgresDatabaseComponent, RedisComponent }
import prchecklist.models._
import prchecklist.repositories._
import prchecklist.services._
import prchecklist.utils.AppConfigFromEnv
import prchecklist.utils.UriStringContext._
import prchecklist.utils.RunnableFuture

class AppServlet extends AppServletBase with AppConfigFromEnv with PostgresDatabaseComponent {
  override val repoRepository = new RepoRepository

  override val checklistRepository = new ChecklistRepository

  override val githubAuthService = new GitHubAuthService

  override val redis = new Redis

  override val http = new Http
}

trait AppServletBase extends ScalatraServlet
    with GitHubConfig
    // infra
    with GitHubHttpClientComponent
    with DatabaseComponent
    with RedisComponent
    // repos
    with GitHubRepositoryComponent
    with RepoRepositoryComponent
    with ProjectConfigRepositoryComponent
    with ChecklistRepositoryComponent
    // model
    with ModelsComponent
    // service
    with GitHubAuthServiceComponent
    with ChecklistServiceComponent
    with SlackNotificationServiceComponent {

  implicit val jsonFormats = JsonSerialization.formats(json4s.NoTypeHints)

  notFound {
    contentType = null
    serveStaticResource() getOrElse resourceNotFound()
  }

  def getVisitor: Option[Visitor] = {
    for {
      login <- session.get("userLogin")
      accessToken <- session.get("accessToken")
    } yield {
      Visitor(login = login.asInstanceOf[String], accessToken = accessToken.asInstanceOf[String])
    }
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
  private def requireGitHubRepo(repoOwner: String, repoName: String)(f: Repo => Any): Any = {
    repoRepository.get(repoOwner, repoName).run match {
      case Some(repo) =>
        f(repo)

      case None =>
        // TODO show repo registration form
        NotFound("Repository not found")
    }
  }

  private def requireReleaseChecklist(repoOwner: String, repoName: String, pullRequestNumber: Int, stage: Option[String])(f: (Repo, ReleaseChecklist) => Any): Any = {
    requireVisitor {
      visitor =>
    }
  }

  private def requireChecklist(repoOwner: String, repoName: String, pullRequestNumber: Int, stage: Option[String])(f: (Repo, ReleaseChecklist) => Any): Any = {
    requireVisitor {
      visitor =>
        requireGitHubRepo(repoOwner, repoName) {
          repo =>
            // TODO: check visilibity
            val githubAccessor = getVisitor getOrElse repo.defaultUser
            val prWithCommits = newGitHubRepository(githubAccessor)
              .getPullRequestWithCommits(repo, pullRequestNumber).run
            val (checklist, _) = new ChecklistService(githubAccessor).getChecklist(repo, prWithCommits, stage getOrElse "").run
            f(repo, checklist)
        }
    }
  }

  get("/auth") {
    val scheme = request.headers.getOrElse("X-Forwarded-Proto", "http")
    val origin = new java.net.URI(scheme, request.uri.getAuthority, null, null, null)
    val location = request.parameters.getOrElse("location", "/")

    val redirectUri = origin + uri"/auth/callback?location=${location}".toString
    Found(githubAuthService.authorizationURL(redirectUri))
  }

  get("/auth/callback") {
    params.get("code").fold(BadRequest("code required")) {
      code =>
        val visitor = githubAuthService.authorize(code).run
        session += "accessToken" -> visitor.accessToken
        session += "userLogin" -> visitor.login
        Found(request.parameters.get("location").filter(_.startsWith("/")) getOrElse "/")
    }
  }

  val sapTemplate =
    <html>
      <head>
        <title>prchecklist</title>
        <link href="https://fonts.googleapis.com/css?family=Open+Sans:400,400italic,700,700italic|Roboto:400,700" rel="stylesheet" type="text/css"/>
      </head>
      <body style="font-family: 'Open Sans'; display: flex; flex-direction: column">
        <div id="app" style="flex: 1">
        </div>
        <footer style="text-align: center; font-size: small; color: #999; height: 2em;">
          <a href="https://github.com/motemen/prchecklist" style="color: inherit">prchecklist</a>
          &nbsp;{ BuildInfo.version }
        </footer>
      </body>
      <script src="/scripts/app.js"></script>
    </html>

  get("/") {
    requireVisitor {
      _ =>
        contentType = "text/html"
        sapTemplate
    }
  }

  get("/:repoOwner/:repoName/pull/:pullRequestNumber") {
    requireVisitor {
      _ =>
        contentType = "text/html"
        sapTemplate
    }
  }

  get("/:repoOwner/:repoName/pull/:pullRequestNumber/:stage") {
    requireVisitor {
      _ =>
        contentType = "text/html"
        sapTemplate
    }
  }

  ///// JSON API /////

  get("/-/me") {
    JsonSerialization.write(views.User.create(getVisitor.get)) // FIXME get
  }

  get("/-/checklist") {
    requireVisitor {
      visitor =>
        requireChecklist(params('repoOwner), params('repoName), params('pullRequestNumber).toInt, params.get('stage)) {
          (repo, checklist) =>
            JsonSerialization.write(views.Checklist.create(checklist, getVisitor))
        }
    }
  }

  get("/-/pullRequests") {
    requireVisitor {
      visitor =>
        val pullRequests = newGitHubRepository(visitor).listReleasePullRequests(params('repoOwner), params('repoName)).run
    }
  }

  post("/-/checklist/check") {
    requireVisitor {
      visitor =>
        requireChecklist(params('repoOwner), params('repoName), params('pullRequestNumber).toInt, params.get('stage)) {
          (repo, checklist) =>
            val featureNumber = params('featureNumber).toInt
            val checklistService = new ChecklistService(visitor)
            checklistService.checkChecklist(checklist, visitor, featureNumber).run
            val updatedChecklist = checklistRepository.reloadChecklistChecks(checklist).run
            JsonSerialization.write(views.Checklist.create(updatedChecklist, getVisitor))
        }
    }
  }

  post("/-/checklist/uncheck") {
    requireVisitor {
      visitor =>
        requireChecklist(params('repoOwner), params('repoName), params('pullRequestNumber).toInt, params.get('stage)) {
          (repo, checklist) =>
            val featureNumber = params('featureNumber).toInt
            val checklistService = new ChecklistService(visitor)
            checklistService.uncheckChecklist(checklist, visitor, featureNumber).run
            val updatedChecklist = checklistRepository.reloadChecklistChecks(checklist).run
            JsonSerialization.write(views.Checklist.create(updatedChecklist, getVisitor))
        }
    }
  }

  post("/-/repos") {
    requireVisitor {
      visitor =>
        val repoOwner = params('repoOwner)
        val repoName = params('repoName)

        val fut = for {
          githubRepo <- newGitHubRepository(visitor).getRepo(repoOwner, repoName)
          (repo, created) <- repoRepository.create(githubRepo, visitor.accessToken)
        } yield repo

        val repo = fut.run
        JsonSerialization.write(views.Repo(fullName = repo.fullName))
    }
  }

  get("/-/news") {
    requireVisitor {
      visitor =>
        val githubRepository = newGitHubRepository(visitor)
        val fut = githubRepository.listStarredRepos() flatMap {
          repos =>
            Future.sequence(
              repos.map {
                repo =>
                  githubRepository.listReleasePullRequests(repo.owner, repo.name)
              }
            ).map {
                news =>
                  news.sortBy { _.headOption.map(_.updatedAt) }.reverse.filter(_.nonEmpty)
              }
        }
        JsonSerialization.write(fut.run)
    }
  }
}
