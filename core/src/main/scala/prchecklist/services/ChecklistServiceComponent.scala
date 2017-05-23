package prchecklist
package services

import prchecklist.models.ProjectConfig
import prchecklist.models.ProjectConfig.NotificationEvent
import prchecklist.models.GitHubTypes
import org.slf4j.LoggerFactory
import com.github.tarao.nonempty.NonEmpty
import com.sun.org.apache.xalan.internal.utils.FeatureManager.Feature

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
    with services.ReverseRouterComponent
    with repositories.RepoRepositoryComponent
    with repositories.GitHubRepositoryComponent
    with repositories.ProjectConfigRepositoryComponent
    with repositories.ChecklistRepositoryComponent
      =>

  class ChecklistService(githubAccessor: GitHubAccessible) {
    def logger = LoggerFactory.getLogger(getClass)

    val githubRepository = self.newGitHubRepository(githubAccessor)
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

    private[this] def ifOption(b: Boolean)(s: => String) = if (b) Some(s) else None

    private[this] def buildMessage(checklist: ReleaseChecklist, checklistUri: java.net.URI, checkerUser: Visitor, featurePRNumber: Int, events: Set[ProjectConfig.NotificationEvent]): String = {

      List(
        ifOption (events.contains(ProjectConfig.NotificationEvent.EventOnCheck)) {
          val title = checklist.featurePullRequest(featurePRNumber).map(_.title) getOrElse "(unknown)"
          s"""[<$checklistUri|${checklist.repo.fullName} #${checklist.pullRequest.number}>] #$featurePRNumber "$title" checked by ${checkerUser.login}"""
        },
        ifOption (events.contains(ProjectConfig.NotificationEvent.EventOnComplete)) {
          s"""[<$checklistUri|${checklist.repo.fullName} #${checklist.pullRequest.number}>] Checklist completed! :tada:"""
        }
      ).flatten.mkString("\n")
    }

    /**
      * Checks one item in a checklist. The most important part of the prchecklist.
      * On successful check, it sends notification based on the prchecklist.yml configuration.
      * @param checklist
      * @param checkerUser
      * @param featurePRNumber
      * @return
      */
    def checkChecklist(checklist: ReleaseChecklist, checkerUser: Visitor, featurePRNumber: Int): Future[ReleaseChecklist] = {
      // TODO: handle errors
      val checklistUri = reverseRouter.checklistUri(checklist) // XXX this must be outside of Future
      val fut = checklistRepository.createCheck(checklist, checkerUser, featurePRNumber)
      fut.onSuccess {
        case updatedChecklist =>
          projectConfigRepository.loadProjectConfig(checklist.repo, s"pull/${checklist.pullRequest.number}/head") andThen {
            case Success(Some(config)) =>
              val events: List[ProjectConfig.NotificationEvent] =
                // always: "on_check" event
                List(ProjectConfig.NotificationEvent.EventOnCheck) ++
                // only when all are checked: "on_complete" event
                ifOption (updatedChecklist.allChecked) { ProjectConfig.NotificationEvent.EventOnComplete }

              Future.sequence {
                config.notification.getChannelsWithAssociatedEvents(events).map {
                  case (channel, eventsForCh) =>
                    val message = buildMessage(checklist, checklistUri, checkerUser, featurePRNumber, eventsForCh)
                    if (message.isEmpty) {
                      Future.successful(())
                    } else {
                      slackNotificationService.send(channel.url, message)
                    }
                }
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
