package prchecklist.test

import prchecklist.models._

object Factory {
  def createGitHubPullRequest: GitHubTypes.PullRequest =
    GitHubTypes.PullRequest(
      number = 1,
      title = "cool feature",
      body = "cool cool cool",
      state = "open",
      head = createGitHubCommitRef.copy(ref = "feature-1"),
      base = createGitHubCommitRef.copy(ref = "master"),
      commits = 1
    )

  def createGitHubCommit: GitHubTypes.Commit =
    GitHubTypes.Commit(
      sha = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
      commit = GitHubTypes.CommitDetail(
        message = "commit message"
      )
    )

  def createGitHubCommitRef: GitHubTypes.CommitRef =
    GitHubTypes.CommitRef(GitHubTypes.Repo("owner/name", false), "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "branch")
}
