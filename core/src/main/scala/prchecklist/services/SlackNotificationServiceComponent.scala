package prchecklist.services

import prchecklist.infrastructure.HttpComponent
import prchecklist.utils

import scala.concurrent.Future

case class SlackMessage(text: String)

case class SlackResponse(
  ok: Boolean,
  error: Option[String]
)

trait SlackNotificationServiceComponent {
  this: HttpComponent =>

  object slackNotificationService {
    def send(url: String, text: String): Future[Unit] = {
      http.postJson(url, SlackMessage(text))
    }
  }
}
