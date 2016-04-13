package prchecklist.services

import prchecklist.utils.AppConfig

import slick.driver.PostgresDriver.api

trait DatabaseComponent {
  def getDatabase: slick.driver.PostgresDriver.backend.DatabaseDef
}

trait PostgresDatabaseComponent extends DatabaseComponent {
  self: AppConfig =>

  Class.forName("org.postgresql.Driver")

  def getDatabase = api.Database.forURL(databaseUrl)
}
