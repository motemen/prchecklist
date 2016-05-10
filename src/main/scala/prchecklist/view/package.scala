package prchecklist

package object views {
  case class Repo(fullName: String)

  case class PullRequest(url: String, number: Int, title: String, body: String)

  case class Check(url: String, number: Int, title: String, users: List[User])

  case class User(login: String)

  case class Checklist(
    repo: Repo,
    pullRequest: PullRequest,
    stage: String,
    checks: List[Check])

  case class SuccessfulResult()

  object Checklist {
    def from(checklist: prchecklist.models.ModelsComponent#ReleaseChecklist): Checklist = Checklist(
      repo = Repo(fullName = checklist.repo.fullName),
      pullRequest = PullRequest(
        url = checklist.pullRequestUrl,
        number = checklist.pullRequest.number,
        title = checklist.pullRequest.title,
        body = checklist.pullRequest.body
      ),
      stage = checklist.stage,
      checks = checklist.checks.map {
        case (nr, check) =>
          Check(
            url = checklist.featurePullRequestUrl(nr),
            number = nr,
            title = check.pullRequest.title,
            users = check.checkedUsers.map(u => User(login = u.login))
          )
      }.toList
    )
  }
}
