package prchecklist.test

import org.scalatest._

trait WithTestDatabase extends BeforeAndAfterAll {
  self: Suite =>

  override def beforeAll(): Unit = {
    super.beforeAll()

    import scala.sys.process._
    import scala.language.postfixOps

    "dropdb prchecklist_test" ###
    "createdb prchecklist_test" #&&
    "psql prchecklist_test -f db/prchecklist.sql" !!

    "redis-cli FLUSHDB" !!
  }
}
