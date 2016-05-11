package prchecklist.services

import prchecklist.utils

import scala.concurrent.Future

case class SlackMessage(text: String)

case class SlackResponse(
  ok: Boolean,
  error: Option[String]
)

trait SlackNotificationServiceComponent {
  this: utils.HttpComponent =>

  object slackNotificationService {
    def send(url: String, text: String): Future[Unit] = {
      http.postJsonDiscardResult(url, SlackMessage(text))
    }
  }
}
