package prchecklist.services

import prchecklist.utils.AppConfig

trait DatabaseComponent {
  def getDatabase: slick.driver.JdbcDriver#Backend#Database
}

trait PostgresDatabaseComponent extends DatabaseComponent {
  self: AppConfig =>

  import slick.driver.PostgresDriver.api

  Class.forName("org.postgresql.Driver")

  def getDatabase = api.Database.forURL(databaseUrl)
}
