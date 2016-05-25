package prchecklist.services

import prchecklist.infrastructure
import prchecklist.services
import prchecklist.repositories
import prchecklist.models
import prchecklist.models.GitHubTypes
import org.slf4j.LoggerFactory
import com.github.tarao.nonempty.NonEmpty

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

/**
 * ChecklistServiceComponent is the main logic of prchecklist.
 */
trait ChecklistServiceComponent {
  self: infrastructure.DatabaseComponent
    with models.ModelsComponent
    with services.SlackNotificationServiceComponent
    with repositories.RepoRepositoryComponent
    with repositories.GitHubRepositoryComponent
    with repositories.ProjectConfigRepositoryComponent
    with repositories.ChecklistRepositoryComponent
      =>

  class ChecklistService(githubAccessor: GitHubAccessible) {
    def logger = LoggerFactory.getLogger(getClass)

    val githubRepository = self.githubRepository(githubAccessor)
    val projectConfigRepository = self.projectConfigRepository(githubRepository)

    val rxMergedPullRequestCommitMessage = """^Merge pull request #(\d+) from [^\n]+\s+(.+)""".r

    private def getMergedPullRequests(repo: Repo, commits: List[GitHubTypes.Commit]): Future[Option[NonEmpty[GitHubTypes.PullRequest]]] = {
      Future.sequence {
        commits.flatMap {
          case GitHubTypes.Commit(_, commit) =>
            rxMergedPullRequestCommitMessage.findFirstMatchIn(commit.message) map {
              m =>
                githubRepository.getPullRequest(repo, m.group(1).toInt)
            }
        }
      }.map {
        prs =>
          NonEmpty.fromTraversable(prs)
      }
    }

    // def getReleaseChecklist(repoOwner: String, repoName: String, number: Int, stage: Option[String]): Future[ReleaseChecklist] = {
    //   for {
    //     repo           <- repoRepository.get(repoOwner, repoName).map(_.getOrElse { throw new Exception("Could not load repo") }) // TODO: throw reasonable excep
    //     prWithCommits  <- githubRepository.getPullRequestWithCommits(repo, number)
    //     (checklist, _) <- getChecklist(repo, prWithCommits, stage getOrElse "")
    //     projectConfig  <- projectConfigRepository.loadProjectConfig(repo, prWithCommits.pullRequest.head.sha)
    //   } yield ReleaseChecklist(checklist, projectConfig)
    // }

    // TODO: make stage Option[String]
    def getChecklist(repo: Repo, prWithCommits: GitHubTypes.PullRequestWithCommits, stage: String): Future[(ReleaseChecklist, Boolean)] = {
      val db = getDatabase

      // repo = repoRepository.get(repoOwner, repoName)
      // prWithCommits = githubRepository.getPullRequestWithCommits(repo, prNumber)

      getMergedPullRequests(repo, prWithCommits.commits) flatMap {
        case None =>
          Future.failed(new IllegalStateException("No merged pull requests"))

        case Some(prs) =>
          for {
            (checklistId, checks, created) <- checklistRepository.getChecklist(repo, prWithCommits.pullRequest.number, stage, prs)
            projectConfig <- projectConfigRepository.loadProjectConfig(repo, prWithCommits.pullRequest.head.sha)
          } yield (ReleaseChecklist(checklistId, repo, prWithCommits.pullRequest, stage, prs.toList, checks, projectConfig), created)
      }
    }

    /**
     * checkChecklist is the most important logic
     */
    def checkChecklist(checklist: ReleaseChecklist, checkerUser: Visitor, featurePRNumber: Int): Future[ReleaseChecklist] = {
      // TODO: handle errors
      val fut = checklistRepository.createCheck(checklist, checkerUser, featurePRNumber)
      fut.onSuccess {
        case newChecklist =>
          projectConfigRepository.loadProjectConfig(checklist.repo, s"pull/${checklist.pullRequest.number}/head") andThen {
            case Success(Some(config)) =>
              Future.traverse(config.notification.channels) {
                case (name, ch) =>
                  val title = checklist.featurePullRequest(featurePRNumber).map(_.title) getOrElse "(unknown)"
                  val additionalMssage = if (newChecklist.allChecked) { "\n:tada::tada:All ckecks are done:tada::tada:" } else { "" }
                  slackNotificationService.send(ch.url, s"""[<${checklist.pullRequestUrl}|${checklist.repo.fullName} #${checklist.pullRequest.number}>] <${checklist.featurePullRequestUrl(featurePRNumber)}|#$featurePRNumber "$title"> checked by ${checkerUser.login} ${additionalMssage}""")
              }
          } onFailure {
            case e =>
              logger.warn(s"Error while sending notification: $e")
          }
      }
      fut
    }

    def uncheckChecklist(checklist: ReleaseChecklist, checkerUser: Visitor, featurePRNumber: Int): Future[Unit] = {
      checklistRepository.deleteCheck(checklist, checkerUser, featurePRNumber)
    }
  }
}
