package prchecklist.web

import org.scalatest.{ FunSuite, Matchers }

import org.json4s.native.{ Serialization => JsonSerialization }

class PackageSpec extends FunSuite with Matchers {
  import prchecklist.jsonFormats

  test("JsonSerialization.write") {
    JsonSerialization.write("a\u0010b") should equal ("\"a\\u0010b\"")
  }
}
