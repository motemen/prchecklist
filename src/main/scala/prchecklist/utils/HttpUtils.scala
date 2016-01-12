package prchecklist.utils

import org.json4s
import org.json4s.native.JsonMethods

import scalaj.http.{ BaseHttp, HttpRequest, HttpResponse, HttpOptions }

import scalaz.\/
import scalaz.syntax.std.option._
import scalaz.syntax.either._

import org.slf4j.LoggerFactory

object HttpUtils extends HttpUtils {
  object Http extends BaseHttp
}

trait HttpUtils {
  val Http: BaseHttp

  val allowUnsafeSSL = System.getProperty("http.allowUnsafeSSL", "") == "true"
  val logger = LoggerFactory.getLogger("prchecklist.utils.HttpUtils")

  def httpRequestJson[A](url: String, build: HttpRequest => HttpRequest = identity)(implicit formats: json4s.Formats = json4s.DefaultFormats, mf: Manifest[A]): Throwable \/ A = {
    for {
      body <- httpRequest(url, _.asString, build)
      obj <- \/.fromTryCatchNonFatal { JsonMethods.parse(body).camelizeKeys.extract[A] }
    } yield obj
  }

  def httpRequest[A](url: String, run: HttpRequest => HttpResponse[A], build: HttpRequest => HttpRequest = identity): Throwable \/ A = {
    val httpReq = if (allowUnsafeSSL) {
      build(Http(url)).option(HttpOptions.allowUnsafeSSL)
    } else {
      build(Http(url))
    }
    logger.debug(s"--> ${httpReq.method} ${httpReq.url}")

    val httpRes = run(httpReq)
    logger.debug(s"<-- ${httpReq.method} ${httpReq.url} -- ${httpRes.statusLine}")

    if (httpRes.isSuccess) {
      httpRes.body.right
    } else {
      new Error(s"${httpReq.method} ${httpReq.url} failed: ${httpRes.statusLine}").left
    }
  }
}
