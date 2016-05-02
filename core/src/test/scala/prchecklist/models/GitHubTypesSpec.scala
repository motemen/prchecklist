package prchecklist.models

import prchecklist.test.Factory

import org.scalatest._

class GitHubTypesSpec extends FunSuite with Matchers {
  test("PullRequest#isOpen/isClosed") {
    val pr = Factory.createGitHubPullRequest
    pr.copy(state = "open").isOpen shouldBe true
    pr.copy(state = "closed").isOpen shouldBe false

    pr.copy(state = "open").isClosed shouldBe false
    pr.copy(state = "closed").isClosed shouldBe true
  }
}
