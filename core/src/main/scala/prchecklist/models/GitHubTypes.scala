package prchecklist.models

object GitHubTypes {
  case class User(login: String, avatarUrl: String)

  // https://developer.github.com/v3/pulls/#get-a-single-pull-request
  case class PullRequest(
      number: Int,
      title: String,
      body: String,
      state: String,
      head: CommitRef,
      base: CommitRef,
      commits: Int,
      assignees: List[User],
      user: User) {

    def isOpen = state == "open"
    def isClosed = state == "closed"

    def usersInCharge = if (assignees.length > 0) assignees else List(user)
  }

  case class PullRequestWithCommits(pullRequest: PullRequest, commits: List[Commit])

  // https://developer.github.com/v3/pulls/#list-pull-requests
  case class PullRequestRef(
      number: Int,
      title: String,
      state: String,
      head: CommitRef,
      base: CommitRef,
      updatedAt: java.util.Date) {

    def isOpen = state == "open"
    def isClosed = state == "closed"
  }

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

  // can we tweak json4s to embed CommitDetail into Commit?
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

  // https://developer.github.com/v3/repos/contents/#get-contents
  case class Content(
    `type`: String,
    encoding: String,
    content: String
  ) {
    def fileContent: Option[String] = {
      import org.apache.commons.codec.binary.Base64

      if (`type` == "file") {
        assert(encoding == "base64")
        Some(new String(Base64.decodeBase64(content)))
      } else {
        None
      }
    }
  }
}
