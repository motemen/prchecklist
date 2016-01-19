package prchecklist.services

import org.scalatest._
import org.scalatest.time._

import scala.concurrent.ExecutionContext.Implicits.global

class GitHubRepoServiceSpec extends FunSuite with Matchers with concurrent.ScalaFutures {
  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(5, Millis))

  test("create && get") {
    whenReady(RepoService.get("owner", "name")) {
      repoOption =>
        repoOption shouldBe 'empty
    }

    whenReady(RepoService.create("owner", "name", "accessToken")) {
      case (repo, created) =>
        repo.owner shouldBe "owner"
        repo.name shouldBe "name"
        repo.defaultAccessToken shouldBe "accessToken"
    }

    whenReady(RepoService.get("owner", "name")) {
      repoOption =>
        repoOption shouldBe 'defined
    }
  }
}
