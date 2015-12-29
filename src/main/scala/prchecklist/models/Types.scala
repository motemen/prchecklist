package prchecklist.models

case class Checklist(pullRequest: GitHubPullRequestFull, checks: Map[Int,Check]) {
}

case class Check(pullRequestNumber: Int, checkedUsers: List[User]) {
  def isChecked: Boolean = checkedUsers.nonEmpty
}

case class Visitor(login: String, accessToken: String)

case class User(login: String)
