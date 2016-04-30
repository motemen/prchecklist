package prchecklist.services

import prchecklist.test._
import prchecklist.models._
import prchecklist.utils._

import org.scalatest._
import org.scalatest.time._

class RepoServiceSpec extends FunSuite with Matchers
    with WithTestDatabase
    with RepoRepositoryComponent
    with PostgresDatabaseComponent
    with TestAppConfig
    with TypesComponent
    with GitHubConfig {

  override val repoRepository = new RepoRepository

  test("create && get") {

    repoRepository.get("owner", "name").run shouldBe 'empty

    repoRepository.create(GitHubTypes.Repo("owner/name", false), "accessToken").run match {
      case (repo, created) =>
        repo.owner shouldBe "owner"
        repo.name shouldBe "name"
        repo.defaultAccessToken shouldBe "accessToken"
    }

    repoRepository.get("owner", "name").run shouldBe 'defined
  }
}
