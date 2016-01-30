package prchecklist.test

import prchecklist.models._

object Factory {
  def createGitHubPullRequest: GitHubTypes.PullRequest =
    GitHubTypes.PullRequest(
      number = 1,
      title = "cool feature",
      body = "cool cool cool",
      state = "open",
      head = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "feature-1"),
      base = GitHubTypes.CommitRef(GitHubTypes.Repo("a/b", false), "", "master")
    )

  def createGitHubCommit: GitHubTypes.Commit =
    GitHubTypes.Commit(
      sha = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
      commit = GitHubTypes.CommitDetail(
        message = "commit message"
      )
    )
}
