package prchecklist.models

import com.github.tarao.nonempty.NonEmpty

case class ReleaseChecklist(pullRequest: ReleasePullRequest, checks: Map[Int,Check])

case class ReleasePullRequest(
  repo: GitHubRepo,
  number: Int,
  title: String,
  body: String,
  featurePullRequestNumbers: NonEmpty[Int]
) {
  def url: String = s"https://github.com/${repo.fullName}/pull/$number"
}

case class GitHubRepo(owner: String, project: String) {
  def fullName = s"$owner/$project"
}

case class Check(pullRequestNumber: Int, checkedUsers: List[User]) {
  def isChecked: Boolean = checkedUsers.nonEmpty
}

case class Visitor(login: String, accessToken: String)

case class User(login: String)
