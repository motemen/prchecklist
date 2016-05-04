package prchecklist.services

import prchecklist.utils

import org.json4s
import org.json4s.jackson.Serialization

case class SlackMessage(text: String)

trait SlackNotificationServiceComponent {
  this: utils.HttpComponent =>

  object slackNotificationService {
    def send(url: String, text: String) = {
      implicit val formats = json4s.DefaultFormats
      http(url).postData(Serialization.write(SlackMessage(text)))
    }
  }
}
