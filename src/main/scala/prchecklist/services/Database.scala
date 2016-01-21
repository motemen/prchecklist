package prchecklist.services

import prchecklist.utils.AppConfig

import slick.driver.PostgresDriver.api

object Database extends AppConfig {
  Class.forName("org.postgresql.Driver")

  def get = api.Database.forURL(databaseUrl)
}
