package prchecklist.utils

import org.json4s
import org.json4s.native.JsonMethods

import scalaj.http.{Http, HttpRequest, HttpResponse}

import scalaz.\/
import scalaz.syntax.std.option._
import scalaz.syntax.either._

object HttpUtils {
  def httpRequestJson[A](url: String, build: HttpRequest => HttpRequest = identity)(implicit formats: json4s.Formats = json4s.DefaultFormats, mf: Manifest[A]): Throwable \/ A = {
    for {
      body <- httpRequest(url, _.asString, build)
      obj <- JsonMethods.parse(body).extractOpt[A] \/> new Error("parsing JSON failed")
    } yield obj
  }

  def httpRequest[A](url: String, run: HttpRequest => HttpResponse[A], build: HttpRequest => HttpRequest = identity): Throwable \/ A = {
    val httpReq = build(Http(url))
    println(s"--> ${httpReq.method} ${httpReq.url}")

    val httpRes = run(httpReq)
    println(s"<-- ${httpReq.method} ${httpReq.url} -- ${httpRes.statusLine}")

    if (httpRes.isSuccess) {
      httpRes.body.right
    } else {
      new Error(s"${httpReq.method} ${httpReq.url} failed: ${httpRes.statusLine}").left
    }
  }
}
