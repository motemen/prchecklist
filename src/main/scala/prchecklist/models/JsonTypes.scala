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

  // https://developer.github.com/v3/activity/events/types/#pullrequestevent
  case class GitHubWebhookPullRequestEvent(
      action: String,
      number: Int,
      pullRequest: GitHubPullRequest) {
    def isOpened = action == "opened"
    def isSynchronize = action == "synchronize"
  }

  case class GitHubRepo(
      fullName: String,
      `private`: Boolean,
      url: String) {
    def isPublic = !`private`
  }

  case class GitHubCommit(
    sha: String,
    commit: GitHubCommitDetail)

  case class GitHubCommitDetail(
    message: String)

  case class GitHubCommitRef(
    repo: GitHubRepo,
    sha: String,
    ref: String)
}
