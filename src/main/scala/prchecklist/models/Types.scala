package prchecklist.models

import prchecklist.utils.GitHubHttpClient

case class ReleaseChecklist(id: Int, pullRequest: ReleasePullRequest, checks: Map[Int, Check]) {
  def pullRequestUrl(number: Int) = pullRequest.repo.pullRequestUrl(number)

  def allGreen = checks.values.forall(_.isChecked)
}

case class ReleasePullRequest(
    repo: GitHubRepo,
    number: Int,
    title: String,
    body: String,
    featurePullRequests: List[PullRequestReference]) {

  def url = repo.pullRequestUrl(number)

  def featurePRNumbers = featurePullRequests.map(_.number)
}

case class ReleasePullRequestReference(
  repo: GitHubRepo,
  number: Int,
  title: String)

case class PullRequestReference(number: Int, title: String)

// A GitHubRepo is a GitHub repository registered to prchecklist with default access token (of the user registered it).
// XXX: Rename to Repo?
case class GitHubRepo(id: Int, owner: String, name: String, defaultAccessToken: String) {
  def fullName = s"$owner/$name"

  def pullRequestUrl(number: Int) = s"$url/pull/$number"

  def url = s"https://${GitHubConfig.domain}/$fullName"

  def defaultUser = RepoDefaultUser(defaultAccessToken)
}

case class Check(pullRequest: PullRequestReference, checkedUsers: List[User]) {
  def isChecked: Boolean = checkedUsers.nonEmpty

  def isCheckedBy(user: UserLike) = checkedUsers.exists(_.login == user.login)
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

trait UserLike extends GitHubConfig {
  val login: String

  def avatarUrl: String = s"$githubOrigin/$login.png"
}
