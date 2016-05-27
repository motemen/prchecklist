package prchecklist.models

/**
  * Represents project.yml
  *
  *   stages:
  *     - staging
  *     - production
  *   notification:
  *     channels:
  *       default:
  *         url: https://slack.com/xxxxx
  */
object ProjectConfig {
  case class Notification(channels: Map[String, Channel])

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
