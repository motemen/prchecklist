package prchecklist.repositories

import org.scalatest._

class ProjectConfigRepositoryComponentSpec extends FunSuite with Matchers
  with ProjectConfigRepositoryComponent
{
  test("parseProjectConfig") {
    val repo = new ProjectConfigRepository {}
    val conf = repo.parseProjectConfig("""
notification:
  channels:
    default:
      url: https://slack.com/xxxx
""")
    conf.notification.channels("default").url shouldBe "https://slack.com/xxxx"
  }
}
