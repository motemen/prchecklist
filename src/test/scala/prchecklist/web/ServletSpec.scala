package prchecklist.web

import org.scalatest.Matchers
import org.scalatra.test.scalatest._
import prchecklist.MyScalatraServlet

class ServletSpec extends ScalatraFunSuite with Matchers {
  addServlet(classOf[MyScalatraServlet], "/*")

  test("/") {
    get("/") {
      status should equal (200)
    }
  }
}
