package prchecklist.models

import prchecklist.utils.AppConfig

trait TypesComponent {
  self: GitHubConfig =>

  case class ReleaseChecklist(id: Int, repo: Repo, pullRequest: GitHubTypes.PullRequest, stage: String, featurePullRequests: List[PullRequestReference], checks: Map[Int, Check]) {
    def pullRequestUrl(number: Int) = repo.pullRequestUrl(number)

    def allGreen = checks.values.forall(_.isChecked)

    def featurePRNumbers = featurePullRequests.map(_.number)
  }

  case class PullRequestReference(number: Int, title: String)

  // A Repo is a GitHub repository registered to prchecklist with default access token (of the user registered it).
  // Do not get confused with GitHubTypes.Repo (TODO: rename GitHubTypes.Repo)
  case class Repo(id: Int, owner: String, name: String, defaultAccessToken: String) {
    def fullName = s"$owner/$name"

    def pullRequestUrl(number: Int) = s"$url/pull/$number"

    def url = s"${githubOrigin}/$fullName"

    def defaultUser = RepoDefaultUser(defaultAccessToken)
  }

  case class Check(pullRequest: PullRequestReference, checkedUsers: List[User]) {
    def isChecked: Boolean = checkedUsers.nonEmpty

    def isCheckedBy(user: TypesComponent#UserLike) = checkedUsers.exists(_.login == user.login)
  }

  // A Visitor is a GitHub user equipped with access token.
  case class Visitor(login: String, accessToken: String) extends UserLike with GitHubAccessible

  case class User(login: String) extends UserLike

  case class RepoDefaultUser(accessToken: String) extends GitHubAccessible

  // GitHubAccessible is a trait representing entities who grants access to GitHub
  // on their behalves.
  trait GitHubAccessible {
    val accessToken: String
  }

  trait UserLike {
    val login: String

    def avatarUrl = s"${githubOrigin}/${login}.png"
  }
}
