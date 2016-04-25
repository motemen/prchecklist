package prchecklist.test

import prchecklist.utils.AppConfig

trait TestAppConfig extends AppConfig {
  val githubClientId = ""
  val githubClientSecret = ""
  val githubDefaultToken = ""
  val databaseUrl = "jdbc:postgresql:prchecklist_test"
  val redisUrl = "redis://127.0.0.1:6379"
}
