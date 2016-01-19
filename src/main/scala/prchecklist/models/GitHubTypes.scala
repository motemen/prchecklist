package prchecklist.models

object GitHubTypes {
  case class User(login: String)

  // https://developer.github.com/v3/pulls/#get-a-single-pull-request
  case class PullRequest(
    number: Int,
    url: String,
    title: String,
    body: String,
    head: CommitRef,
    base: CommitRef)

  // https://developer.github.com/v3/activity/events/types/#pullrequestevent
  case class WebhookPullRequestEvent(
      action: String,
      number: Int,
      pullRequest: PullRequest,
      repository: Repo) {
    def isOpened = action == "opened"
    def isSynchronize = action == "synchronize"
  }

  case class Repo(
      fullName: String,
      `private`: Boolean,
      url: String) {
    def isPublic = !`private`
  }

  case class Commit(
    sha: String,
    commit: CommitDetail)

  case class CommitDetail(
    message: String)

  case class CommitRef(
    repo: Repo,
    sha: String,
    ref: String)

  case class IssueComment(body: String)

  case class CommitStatus(
    state: String,
    targetUrl: String,
    description: String,
    context: String = "prchecklist")
}