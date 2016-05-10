package prchecklist.services

import prchecklist.utils

import scalaz.concurrent.Task

case class SlackMessage(text: String)

case class SlackResponse(
  ok: Boolean,
  error: Option[String]
)

trait SlackNotificationServiceComponent {
  this: utils.HttpComponent =>

  object slackNotificationService {
    def send(url: String, text: String): Task[Unit] = {
      http.postJsonDiscardResult(url, SlackMessage(text))
    }
  }
}
