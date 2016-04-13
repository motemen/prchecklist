package prchecklist.utils

import prchecklist.models.GitHubConfig

import org.json4s
import org.json4s.jackson.JsonMethods

import org.slf4j.LoggerFactory

import scalaj.http.HttpOptions.HttpOption
import scalaj.http.{ BaseHttp, HttpRequest, HttpResponse, HttpOptions }

import scalaz.concurrent.Task

import java.io.InputStream

object Http extends Http with AppConfigFromEnv

trait Http extends BaseHttp {
  self: AppConfig =>

  def logger = LoggerFactory.getLogger(getClass)

  override def apply(url: String): HttpRequest = {
    super.apply(url)
      .options(defaultHttpOptions)
      .headers(defaultHttpHeaders)
  }

  def defaultHttpOptions: Seq[HttpOption] = {
    if (httpAllowUnsafeSSL) {
      Seq(HttpOptions.allowUnsafeSSL)
    } else {
      Seq.empty
    }
  }

  def defaultHttpHeaders: Map[String, String] = {
    Map.empty
  }

  def postJson[P <: AnyRef, R](url: String, payload: P)(implicit formats: json4s.Formats = json4s.DefaultFormats, mfP: Manifest[P], mfR: Manifest[R]): Task[R] = {
    val httpReq = apply(url).postData(org.json4s.jackson.Serialization.write(payload))
    requestJson(httpReq)
  }

  def getJson[R](url: String)(implicit formats: json4s.Formats = json4s.DefaultFormats, mf: Manifest[R]): Task[R] = {
    requestJson(apply(url))
  }

  protected def requestJson[R](req: HttpRequest)(implicit formats: json4s.Formats = json4s.DefaultFormats, mf: Manifest[R]): Task[R] = {
    doRequest(req) {
      is =>
        JsonMethods.parse(is).camelizeKeys.extract[R]
    }
  }

  protected def doRequest[A](httpReq: HttpRequest)(parser: InputStream => A): Task[A] = {
    logger.debug(s"--> ${httpReq.method} ${httpReq.url}")

    Task {
      val httpRes = httpReq.exec {
        case (code, headers, is) =>
          // https://developer.github.com/v3/#rate-limiting
          val limitRateInfo = for {
            remaining <- headers.get("X-RateLimit-Remaining")
            limit <- headers.get("X-RateLimit-Limit")
          } yield s" [$remaining/$limit]"

          logger.debug(s"<-- ${httpReq.method} ${httpReq.url} -- ${code}${limitRateInfo.mkString}")

          if (code >= 400) {
            throw new Error(s"${httpReq.method} ${httpReq.url} failed: ${code}")
          }

          parser(is)
      }
      if (httpRes.isSuccess) {
        httpRes.body
      } else {
        throw new Error(s"${httpReq.method} ${httpReq.url} failed: ${httpRes.statusLine}")
      }
    }
  }
}
