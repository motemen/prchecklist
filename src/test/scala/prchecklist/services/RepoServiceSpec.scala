package prchecklist.services

import prchecklist.test._
import prchecklist.models._
import prchecklist.utils._

import org.scalatest._
import org.scalatest.time._

class RepoServiceSpec extends FunSuite with Matchers with RepoServiceComponent with PostgresDatabaseComponent with TestAppConfig {
  override val repoService = new RepoService

  test("create && get") {

    repoService.get("owner", "name").run shouldBe 'empty

    repoService.create(GitHubTypes.Repo("owner/name", false), "accessToken").run match {
      case (repo, created) =>
        repo.owner shouldBe "owner"
        repo.name shouldBe "name"
        repo.defaultAccessToken shouldBe "accessToken"
    }

    repoService.get("owner", "name").run shouldBe 'defined
  }
}
