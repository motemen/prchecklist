package prchecklist.models

/**
 * Represents project.yml
 *
 *   stages:
 *     - staging
 *     - production
 *   notification:
 *     events:
 *       on_check:
 *         - default
 *         - check
 *       on_complete:
 *         - default
 *     channels:
 *       default:
 *         url: https://slack.com/xxxxx
 *       check:
 *         url: https://slack.com/xxxxx
 */
object ProjectConfig {
  object NotificationEvent {
    val EventOnCheck: NotificationEvent    = "on_check"
    val EventOnComplete: NotificationEvent = "on_complete"
  }

  type NotificationEvent = String
  type ChannelName = String

  case class Notification(
      events: Option[Map[NotificationEvent, List[ChannelName]]],
      channels: Map[ChannelName, Channel]) {

    def getChannels(event: NotificationEvent): List[Channel] = {
      val names = events match {
        case None      => List("default")
        case Some(map) => map.getOrElse(event, List())
      }
      names.flatMap { name => channels.get(name).toList }
    }

    /**
      * Returns channel names with thier associated event names.
      * e.g. For prchecklist.yml such:
      *
      *   notification:
      *     events:
      *       on_check:
      *         - default
      *       on_complete:
      *         - default
      *         - ch_completion
      *
      * Returns { default => {on_check, on_complete}, ch_completion => {on_complete} }
      * for parameter events = {on_check, on_complete}.
      *
      * If "notification" section is not given, the default channel is always returned.
      * @param events Event names used for retrieving channels
      * @return The list of (channel name, event names associated)
      */
    def getChannelsWithAssociatedEvents(events: Traversable[NotificationEvent]): Map[Channel, Set[NotificationEvent]] =
      events.flatMap {
        event =>
          getChannels(event) map {
            channel => (channel, event)
          }
      }
        .groupBy { case (channel, event) => channel }
        .mapValues { _.map(_._2).toSet }
  }

  case class Channel(url: String)
}

case class ProjectConfig(
  stages: Option[List[String]],
  notification: ProjectConfig.Notification
) {
  def defaultStage: Option[String] = stages.flatMap(_.headOption)
}

trait ModelsComponent {
  self: GitHubConfig =>

  /**
    * ReleaseChecklist is the topmost object which aggregates all single release checklist related information.
    * This represents a checklist state built from Pull Requests obtained by GitHub API
    * and check states from the prchecklist database.
    * @param id
    * @param repo
    * @param pullRequest
    * @param stage
    * @param featurePullRequests
    * @param checks
    * @param projectConfig
    */
  case class ReleaseChecklist(
      id: Int,
      repo: Repo,
      pullRequest: GitHubTypes.PullRequest,
      stage: String, // TODO: be Option[String]
      featurePullRequests: List[GitHubTypes.PullRequest],
      checks: Map[Int, Check],
      projectConfig: Option[ProjectConfig]
  ) {
    def pullRequestUrl = repo.pullRequestUrl(pullRequest.number)

    def featurePullRequestUrl(number: Int) = repo.pullRequestUrl(number)

    def allChecked = checks.values.forall(_.isChecked)

    def featurePullRequest(number: Int): Option[GitHubTypes.PullRequest] =
      featurePullRequests.find(_.number == number)
  }

  // A Repo is a GitHub repository registered to prchecklist with default access token (of the user registered it).
  // Do not get confused with GitHubTypes.Repo (TODO: rename GitHubTypes.Repo)
  case class Repo(id: Int, owner: String, name: String, defaultAccessToken: String) {
    def fullName = s"$owner/$name"

    def pullRequestUrl(number: Int) = s"$url/pull/$number"

    def url = s"${githubOrigin}/$fullName"

    def defaultUser = RepoDefaultUser(defaultAccessToken)
  }

  case class Check(pullRequest: GitHubTypes.PullRequest, checkedUsers: List[User]) {
    def isChecked: Boolean = checkedUsers.nonEmpty

    def isCheckedBy(user: ModelsComponent#UserLike) = checkedUsers.exists(_.login == user.login)
  }

  // A Visitor is a GitHub user equipped with access token.
  case class Visitor(login: String, accessToken: String) extends UserLike with GitHubAccessible

  case class User(login: String) extends UserLike

  case class RepoDefaultUser(accessToken: String) extends GitHubAccessible

  trait UserLike {
    val login: String

    def avatarUrl = s"${githubOrigin}/${login}.png"
  }

  // GitHubAccessible is a trait representing entities who grants access to GitHub
  // on their behalves.
  trait GitHubAccessible {
    val accessToken: String
  }
}
