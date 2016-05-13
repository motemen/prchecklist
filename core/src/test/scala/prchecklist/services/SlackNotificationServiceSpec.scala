package prchecklist.services

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.net.{HttpURLConnection, URL}

import org.mockito.Mockito
import prchecklist.services
import prchecklist.utils
import prchecklist.utils.RunnableFuture
import prchecklist.test
import org.scalatest._
import org.scalatest.mock.MockitoSugar

import scala.concurrent.Future
import scalaj.http.HttpRequest
import scala.concurrent.ExecutionContext.Implicits.global

class SlackNotificationServiceSpec extends FunSuite with Matchers
    with MockitoSugar
    with services.SlackNotificationServiceComponent
    with utils.HttpComponent
    with test.TestAppConfig {

  var httpOutputCapture: OutputStream = null

  override def http: Http = new Http {
    override protected def doRequest[A](httpReq: HttpRequest)(parser: (InputStream) => A): Future[A] = {
      val spy = Mockito.spy(new URL(httpReq.url).openConnection().asInstanceOf[HttpURLConnection])

      Mockito.doNothing().when(spy).connect()

      // Not "doReturn(s)" for a quick hack addressing:
      //   [error] ... ambiguous reference to overloaded definition,
      //   [error] both method doReturn in object Mockito of type (x$1: Any, x$2: Object*)org.mockito.stubbing.Stubber
      //   [error] and  method doReturn in object Mockito of type (x$1: Any)org.mockito.stubbing.Stubber
      //   [error] match argument types (java.io.ByteArrayOutputStream)
      Mockito.doReturn(httpOutputCapture, httpOutputCapture).when(spy).getOutputStream

      httpReq.connectFunc(httpReq, spy)

      Future {
        parser(new ByteArrayInputStream("ok".toArray.map(_.toByte)))
      }
    }
  }

  test("send") {
    import org.json4s.JsonAST.JString

    httpOutputCapture = new ByteArrayOutputStream()
    slackNotificationService.send("http://slack.test/", "hello").run

    val json = org.json4s.jackson.parseJson(httpOutputCapture.toString)
    (json \ "text") shouldEqual JString("hello")
  }
}
