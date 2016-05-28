package prchecklist.services

import prchecklist.infrastructure.HttpComponent
import prchecklist.utils

import scala.concurrent.{ExecutionContext, Future}

case class SlackMessage(text: String)

case class SlackResponse(
  ok: Boolean,
  error: Option[String]
)

trait SlackNotificationServiceComponent {
  this: HttpComponent =>

  def slackNotificationService: SlackNotificationService = SlackNotificationService

  trait SlackNotificationService {
    def send(url: String, text: String)(implicit ec: ExecutionContext): Future[Unit]
  }

  object SlackNotificationService extends SlackNotificationService {
    def send(url: String, text: String)(implicit ec: ExecutionContext): Future[Unit] = {
      http.postJson(url, SlackMessage(text))
    }
  }
}
