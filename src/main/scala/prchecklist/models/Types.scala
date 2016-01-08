package prchecklist.models

import com.github.tarao.nonempty.NonEmpty

case class ReleaseChecklist(pullRequest: ReleasePullRequest, checks: Map[Int, Check]) {
  def pullRequestUrl(number: Int) = pullRequest.repo.pullRequestUrl(number)

  def allGreen = checks.values.forall(_.isChecked)
}

case class PullRequestReference(number: Int, title: String)

case class ReleasePullRequest(
    repo: GitHubRepo,
    number: Int,
    title: String,
    body: String,
    featurePullRequests: List[PullRequestReference]) {

  def url = repo.pullRequestUrl(number)
}

case class GitHubRepo(owner: String, name: String) {
  def fullName = s"$owner/$name"

  def pullRequestUrl(number: Int) = s"$url/pull/$number"

  def url = s"https://${GitHubConfig.domain}/$fullName"
}

case class Check(pullRequest: PullRequestReference, checkedUsers: List[User]) {
  def isChecked: Boolean = checkedUsers.nonEmpty
}

case class Visitor(login: String, accessToken: String) extends UserLike

case class User(login: String) extends UserLike

trait UserLike extends GitHubConfig {
  val login: String

  def avatarUrl: String = s"$githubOrigin/$login.png"
}
