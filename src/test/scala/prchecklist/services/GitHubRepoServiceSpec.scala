package prchecklist.services

import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global

class GitHubRepoServiceSpec extends FunSuite with Matchers with concurrent.ScalaFutures {
  test("create && get") {
    whenReady(GitHubRepoService.get("owner", "name")) {
      repoOption =>
        repoOption shouldBe 'empty
    }

    whenReady(GitHubRepoService.create("owner", "name", "accessToken")) {
      case (repo, created) =>
        repo.owner shouldBe "owner"
        repo.name shouldBe "name"
        repo.defaultAccessToken shouldBe "accessToken"
    }

    whenReady(GitHubRepoService.get("owner", "name")) {
      repoOption =>
        repoOption shouldBe 'defined
    }
  }
}
