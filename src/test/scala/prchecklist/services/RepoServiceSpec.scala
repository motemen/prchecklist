package prchecklist.services

import prchecklist.models._

import org.scalatest._
import org.scalatest.time._

class RepoServiceSpec extends FunSuite with Matchers {
  test("create && get") {
    RepoService.get("owner", "name").run shouldBe 'empty

    RepoService.create(GitHubTypes.Repo("owner/name", false), "accessToken").run match {
      case (repo, created) =>
        repo.owner shouldBe "owner"
        repo.name shouldBe "name"
        repo.defaultAccessToken shouldBe "accessToken"
    }

    RepoService.get("owner", "name").run shouldBe 'defined
  }
}
