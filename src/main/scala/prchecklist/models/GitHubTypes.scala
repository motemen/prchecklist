package prchecklist.models

object JsonTypes {
  case class GitHubUser(login: String)

  case class GitHubPullRequest(
    number: Int,
    url: String,
    title: String,
    body: String
  )

  case class GitHubCommit(
    sha: String,
    commit: GitHubCommitDetail
  )

  case class GitHubCommitDetail(
    message: String
  )
}

import JsonTypes._

case class GitHubPullRequestFull(repository: GitHubRepository, detail: GitHubPullRequest, commits: List[GitHubCommit]) {
  def mergedPullRequestNumbers: List[Int] = {
    commits.flatMap {
      c => """^Merge pull request #(\d+) """.r.findFirstMatchIn(c.commit.message) map {
        m => m.group(1).toInt
      }
    }
  }
}

case class GitHubRepository(owner: String, project: String) {
  def fullName = s"$owner/$project"
}

// Cache this
case class ReleasePullRequest(
  repository: GitHubRepository,
  number: Int,
  title: String,
  body: String,
  url: String,
  featurePullRequestNumbers: Seq[Int]
)
