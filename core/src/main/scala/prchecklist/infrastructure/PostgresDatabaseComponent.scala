package prchecklist.infrastructure

import prchecklist.utils.AppConfig
import scala.collection.concurrent

object PostgresDatabaseComponent {
  import slick.driver.PostgresDriver.api

  Class.forName("org.postgresql.Driver")

  private val dbMap: concurrent.Map[String, slick.driver.JdbcDriver#Backend#Database]
    = concurrent.TrieMap()

  def getDatabase(url: String): slick.driver.JdbcDriver#Backend#Database = {
    dbMap.getOrElseUpdate(url, api.Database.forURL(url))
  }
}

trait PostgresDatabaseComponent extends DatabaseComponent {
  self: AppConfig =>

  override def getDatabase = PostgresDatabaseComponent.getDatabase(databaseUrl)
}
