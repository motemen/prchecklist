package prchecklist.services

import slick.driver.PostgresDriver.api

object Database {
  Class.forName("org.postgresql.Driver")

  def get = api.Database.forURL(System.getProperty("database.url", "jdbc:postgresql:prchecklist_local"))
}
