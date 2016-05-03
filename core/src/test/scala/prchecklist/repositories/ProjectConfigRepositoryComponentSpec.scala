package prchecklist.repositories

import org.scalatest._

class ProjectConfigRepositoryComponentSpec extends FunSuite with Matchers
  with ProjectConfigRepositoryComponent
{
  test("parseProjectConfig") {
    val repo = new ProjectConfigRepository {}
    val conf = repo.parseProjectConfig("""
channels:
  default:
    url: https://slack.com/xxxx
""")
    conf.channels("default").url shouldBe "https://slack.com/xxxx"
  }
}
