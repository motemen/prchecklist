package prchecklist.services

import prchecklist.infrastructure
import prchecklist.services
import prchecklist.repositories
import prchecklist.repositories.ProjectConfig
import prchecklist.models
import prchecklist.models.GitHubTypes
import org.slf4j.LoggerFactory
import com.github.tarao.nonempty.NonEmpty
import prchecklist.repositories.ProjectConfig.NotificationEvent

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

    def getChecklist(repo: Repo, prWithCommits: GitHubTypes.PullRequestWithCommits, stage: String): Future[(ReleaseChecklist, Boolean)] = {
      val db = getDatabase

      // repo = repoRepository.get(repoOwner, repoName)
      // prWithCommits = githubRepository.getPullRequestWithCommits(repo, prNumber)

      getMergedPullRequests(repo, prWithCommits.commits) flatMap {
        case None =>
          Future.failed(new IllegalStateException("No merged pull requests"))

        case Some(prs) =>
          checklistRepository.getChecks(repo, prWithCommits.pullRequest.number, stage, prs).map {
            case (checklistId, checks, created) =>
              (ReleaseChecklist(checklistId, repo, prWithCommits.pullRequest, stage, prs.toList, checks), created)
          }
      }
    }

    /**
     * checkChecklist is the most important logic
     */
    def checkChecklist(checklist: ReleaseChecklist, checkerUser: Visitor, featurePRNumber: Int): Future[ReleaseChecklist] = {
      // TODO: handle errors
      val fut = checklistRepository.createCheck(checklist, checkerUser, featurePRNumber)
      fut.onSuccess {
        case updatedChecklist =>
          projectConfigRepository.loadProjectConfig(checklist.repo, s"pull/${checklist.pullRequest.number}/head") andThen {
            case Success(Some(config)) =>
              def buildMessage(events: List[ProjectConfig.NotificationEvent]): String = {
                def ifOption(b: Boolean)(s: => String) = if (b) Some(s) else None

                List(
                  ifOption (events.contains(ProjectConfig.NotificationEvent.EventOnCheck)) {
                    val title = checklist.featurePullRequest(featurePRNumber).map(_.title) getOrElse "(unknown)"
                    s"[<${checklist.pullRequestUrl}|${checklist.repo.fullName} #${checklist.pullRequest.number}>]" +
                      s""" <${checklist.featurePullRequestUrl(featurePRNumber)}|#$featurePRNumber "$title"> checked by ${checkerUser.login}"""
                  },
                  ifOption (events.contains(ProjectConfig.NotificationEvent.EventOnComplete)) {
                    ":tada: All checked"
                  }
                ).flatten.mkString("\n")
              }

              val events: List[ProjectConfig.NotificationEvent] =
                List(ProjectConfig.NotificationEvent.EventOnCheck) ++
                  (if (updatedChecklist.allGreen) { List(ProjectConfig.NotificationEvent.EventOnComplete) } else { List() })

              val channelAndEvents = events.flatMap {
                event =>
                  config.notification.getChannels(event) map {
                    channel => (channel, event)
                  }
              } .groupBy { case (channel, event) => channel }
                .mapValues { _.map { case (channel, event) => event } }

              Future.sequence {
                channelAndEvents.map {
                  case (channel, eventsForCh) =>
                    val message = buildMessage(eventsForCh)
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
