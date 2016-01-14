package prchecklist.utils

import org.json4s
import org.json4s.native.JsonMethods

import scalaj.http.{ BaseHttp, HttpRequest, HttpResponse, HttpOptions }

import scalaz.\/
import scalaz.syntax.either._

import org.slf4j.LoggerFactory

class GitHubHttpClient(accessToken: String) extends HttpUtils {
  override def defaultBuild(req: HttpRequest): HttpRequest = {
    super.defaultBuild(req).header("Authorization", s"token $accessToken")
  }
}

object HttpUtils extends HttpUtils

trait HttpUtils {
  object Http extends BaseHttp

  val allowUnsafeSSL = System.getProperty("http.allowUnsafeSSL", "") == "true"
  val logger = LoggerFactory.getLogger("prchecklist.utils.HttpUtils")

  def defaultBuild(req: HttpRequest): HttpRequest = {
    if (allowUnsafeSSL) {
      req.option(HttpOptions.allowUnsafeSSL)
    } else {
      req
    }
  }

  def requestJson[A](url: String, build: HttpRequest => HttpRequest = identity)(implicit formats: json4s.Formats = json4s.DefaultFormats, mf: Manifest[A]): Throwable \/ A = {
    for {
      body <- request(url, _.asString, build)
      obj <- \/.fromTryCatchNonFatal { JsonMethods.parse(body).camelizeKeys.extract[A] }
    } yield obj
  }

  def request[A](url: String, run: HttpRequest => HttpResponse[A], build: HttpRequest => HttpRequest = identity): Throwable \/ A = {
    val httpReq = build(defaultBuild(Http(url)))
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
