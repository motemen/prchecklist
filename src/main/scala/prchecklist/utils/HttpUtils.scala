package prchecklist.utils

import prchecklist.models.GitHubConfig

import org.json4s
import org.json4s.native.JsonMethods

import org.slf4j.LoggerFactory

import scalaj.http.HttpOptions.HttpOption
import scalaj.http.{ BaseHttp, HttpRequest, HttpResponse, HttpOptions }

import scalaz.concurrent.Task

import java.io.InputStream

// TODO: do not extend Http, simply use it
// TODO: receive GitHubAccessible
class GitHubHttpClient(accessToken: String) extends HttpUtils with GitHubConfig {
  override def defaultHttpHeaders: Map[String,String] = {
    super.defaultHttpHeaders + ("Authorization" -> s"token $accessToken")
  }

  override def apply(url: String): HttpRequest = {
    super.apply(s"$githubApiBase$url")
  }
}

// TODO: rename to Http
object HttpUtils extends HttpUtils

trait HttpUtils extends BaseHttp {
  val allowUnsafeSSL = System.getProperty("http.allowUnsafeSSL", "") == "true"

  def logger = LoggerFactory.getLogger(getClass)

  def defaultBuild(req: HttpRequest): HttpRequest = {
    req.options(defaultHttpOptions).headers(defaultHttpHeaders)
  }

  override def apply(url: String): HttpRequest = {
    super.apply(url)
      .options(defaultHttpOptions)
      .headers(defaultHttpHeaders)
  }

  def defaultHttpOptions: Seq[HttpOption] = {
    if (allowUnsafeSSL) {
      Seq(HttpOptions.allowUnsafeSSL)
    } else {
      Seq.empty
    }
  }

  def defaultHttpHeaders: Map[String, String] = {
    Map.empty
  }

  def postJson[P <: AnyRef, R](url: String, payload: P)(implicit formats: json4s.Formats = json4s.DefaultFormats, mfP: Manifest[P], mfR: Manifest[R]): Task[R] = {
    val httpReq = apply(url).postData(org.json4s.native.Serialization.write(payload))
    requestJson(httpReq)
  }

  def getJson[R](url: String)(implicit formats: json4s.Formats = json4s.DefaultFormats, mf: Manifest[R]): Task[R] = {
    requestJson(apply(url))
  }

  def requestJson[R](req: HttpRequest)(implicit formats: json4s.Formats = json4s.DefaultFormats, mf: Manifest[R]): Task[R] = {
    doRequest(req) {
      is =>
        JsonMethods.parse(is).camelizeKeys.extract[R]
    }
  }

  def doRequest[A](httpReq: HttpRequest)(parser: InputStream => A): Task[A] = {
    logger.debug(s"--> ${httpReq.method} ${httpReq.url}")

    Task {
      val httpRes = httpReq.exec({
          case (code, headers, is) =>
            logger.debug(s"<-- ${httpReq.method} ${httpReq.url} -- ${code}")
            parser(is)
        })
      if (httpRes.isSuccess) {
        httpRes.body
      } else {
        throw new Error(s"${httpReq.method} ${httpReq.url} failed: ${httpRes.statusLine}")
      }
    }
  }
}
