package prchecklist.infrastructure

trait DatabaseComponent {
  def getDatabase: slick.driver.JdbcDriver#Backend#Database
}
