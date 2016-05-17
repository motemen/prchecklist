package prchecklist.infrastructure

import java.io.InputStream

import org.json4s
import org.json4s.native.JsonMethods
import org.json4s.native.{Serialization => JsonSerialization}
import org.slf4j.LoggerFactory
import prchecklist.utils.AppConfig

import scala.concurrent.{ExecutionContext, Future}
import scalaj.http.HttpOptions._
import scalaj.http.{BaseHttp, HttpOptions, HttpRequest}

trait HttpComponent {
  self: AppConfig =>

  def http: Http

  class Http extends BaseHttp {
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

    def postJson[P <: AnyRef, R](url: String, payload: P)(implicit formats: json4s.Formats = json4s.DefaultFormats, ec: ExecutionContext, mfP: Manifest[P], mfR: Manifest[R]): Future[R] = {
      val httpReq = apply(url).postData(JsonSerialization.write(payload))
      requestJson(httpReq)
    }

    def getJson[R](url: String)(implicit formats: json4s.Formats = json4s.DefaultFormats, ec: ExecutionContext, mf: Manifest[R]): Future[R] = {
      requestJson(apply(url))
    }

    protected def requestJson[R](req: HttpRequest)(implicit formats: json4s.Formats = json4s.DefaultFormats, ec: ExecutionContext, mf: Manifest[R]): Future[R] = {
      doRequest(req) {
        is =>
          if (mf == manifest[Nothing] || mf == manifest[Unit]) {
            Unit.asInstanceOf[R]
          } else {
            JsonMethods.parse(is).camelizeKeys.extract[R]
          }
      }
    }

    protected def doRequest[A](httpReq: HttpRequest)(parser: InputStream => A)(implicit ec: ExecutionContext): Future[A] = {
      logger.debug(s"--> ${httpReq.method} ${httpReq.url}")

      Future {
        val httpRes = httpReq.exec {
          case (code, headers, is) =>
            // https://developer.github.com/v3/#rate-limiting
            val limitRateInfo = for {
              remaining <- headers.get("X-RateLimit-Remaining")
              limit <- headers.get("X-RateLimit-Limit")
            } yield s" [$remaining/$limit]"

            logger.debug(s"<-- ${httpReq.method} ${httpReq.url} -- ${code}${limitRateInfo.mkString}")

            if (code >= 400) {
              throw FailedHttpResponseException(code, httpReq)
            }

            parser(is)
        }
        assert(httpRes.isNotError)
        httpRes.body
      }
    }
  }

  case class FailedHttpResponseException(code: Int, request: HttpRequest) extends Exception {
    override def toString = s"${request.method} ${request.url} failed: ${code}"
  }
}
