package prchecklist

import prchecklist.models.ModelsComponent

package object views {
  case class Repo(fullName: String)

  case class PullRequest(url: String, number: Int, title: String, body: String)

  case class Check(url: String, number: Int, title: String, users: List[User], checked: Boolean)

  case class User(name: String, avatarUrl: String)

  case class Checklist(
    repo: Repo,
    pullRequest: PullRequest,
    stage: String,
    checks: List[Check],
    allChecked: Boolean)

  object User {
    def create(visitor: ModelsComponent#Visitor): User = User(
      name = visitor.login, avatarUrl = visitor.avatarUrl
    )
  }

  object Checklist {
    def create(checklist: ModelsComponent#ReleaseChecklist, visitor: Option[ModelsComponent#Visitor]): Checklist = Checklist(
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
            users = check.checkedUsers.map(u => User(name = u.login, avatarUrl = u.avatarUrl)),
            checked = visitor.exists(check.isCheckedBy(_))
          )
      }.toList,
      allChecked = checklist.allGreen
    )
  }
}
