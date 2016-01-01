package prchecklist.models

import com.github.tarao.nonempty.NonEmpty

// TODO case class ReleaseChecklist(repo, number, title, body, featurePullRequests, checkers)
case class ReleaseChecklist(pullRequest: ReleasePullRequest, checks: Map[Int, Check])

case class ReleasePullRequest(
    repo: GitHubRepo,
    number: Int,
    title: String,
    body: String,
    // TODO: featurePullRequests
    featurePullRequestNumbers: NonEmpty[Int]) {
  def url: String = s"https://github.com/${repo.fullName}/pull/$number"
}

case class GitHubRepo(owner: String, name: String) {
  def fullName = s"$owner/$name"
}

case class Check(pullRequestNumber: Int, checkedUsers: List[User]) {
  def isChecked: Boolean = checkedUsers.nonEmpty
}

case class Visitor(login: String, accessToken: String)

case class User(login: String) {
  def profileImageUrl: String = s"https://github.com/$login.png"
}
