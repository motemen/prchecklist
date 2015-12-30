package prchecklist.models

object JsonTypes {
  case class GitHubUser(login: String)

  // https://developer.github.com/v3/pulls/#get-a-single-pull-request
  case class GitHubPullRequest(
    number: Int,
    url: String,
    title: String,
    body: String,
    head: GitHubCommitRef,
    base: GitHubCommitRef)

  case class GitHubCommit(
    sha: String,
    commit: GitHubCommitDetail)

  case class GitHubCommitDetail(
    message: String)

  case class GitHubCommitRef(
    sha: String)
}
