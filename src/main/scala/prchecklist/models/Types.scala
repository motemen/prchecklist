package prchecklist.models

import com.github.tarao.nonempty.NonEmpty

// TODO case class ReleaseChecklist(repo, number, title, body, featurePullRequests, checkers)
case class ReleaseChecklist(pullRequest: ReleasePullRequest, checks: Map[Int, Check]) {
  def pullRequestUrl(number: Int) = pullRequest.repo.pullRequestUrl(number)
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

case class User(login: String) extends UserLike {
}

trait UserLike {
  val login: String

  // TODO: use API response
  def profileImageUrl: String = s"https://github.com/$login.png"
}
