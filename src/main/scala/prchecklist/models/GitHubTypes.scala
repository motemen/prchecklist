package prchecklist.models

object GitHubTypes {
  case class User(login: String)

  // https://developer.github.com/v3/pulls/#get-a-single-pull-request
  case class PullRequest(
      number: Int,
      title: String,
      body: String,
      state: String,
      head: CommitRef,
      base: CommitRef,
      commits: Int) {

    def isOpen = state == "open"
    def isClosed = state == "closed"
  }

  case class PullRequestWithCommits(pullRequest: PullRequest, commits: List[Commit])

  // https://developer.github.com/v3/activity/events/types/#pullrequestevent
  case class WebhookPullRequestEvent(
      action: String,
      number: Int,
      pullRequest: PullRequest,
      repository: Repo) {
    def isOpened = action == "opened"
    def isSynchronize = action == "synchronize"
  }

  // Do not get confused with Types.Repo (TODO: rename GitHubTypes.Repo)
  case class Repo(
      fullName: String,
      `private`: Boolean) {
    def isPublic = !`private`

    lazy val Array(owner, name) = fullName.split("/", 2)
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
