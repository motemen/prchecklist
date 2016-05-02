package prchecklist.infrastructure

import prchecklist.utils.AppConfig

trait PostgresDatabaseComponent extends DatabaseComponent {
  self: AppConfig =>

  import slick.driver.PostgresDriver.api

  Class.forName("org.postgresql.Driver")

  def getDatabase = api.Database.forURL(databaseUrl)
}
