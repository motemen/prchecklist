package prchecklist
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

object JettyLauncher {
  def main(args: Array[String]) {
    val port = if (System.getenv("PORT") != null) System.getenv("PORT").toInt else 8080

    val server = new Server(port)
    val context = new WebAppContext()
    context.setContextPath("/")
    context.setResourceBase("src/main/webapp")

    context.addEventListener(new ScalatraListener)
    context.addServlet(classOf[DefaultServlet], "/")

    server.setHandler(context)

    server.start()
    Option(System.getProperty("browserSync.port")).foreach {
      port =>
        import scala.sys.process._
        ("node_modules/.bin/browser-sync" :: "reload" :: "--port" :: port :: Nil).run
    }
    server.join()
  }
}
