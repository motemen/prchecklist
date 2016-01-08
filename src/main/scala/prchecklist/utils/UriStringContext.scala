package prchecklist.utils

import java.net.{ URI, URLEncoder }

object UriStringContext {
  implicit class UriStringContext(val sc: StringContext) extends AnyVal {
    private def encode(s: String) = URLEncoder.encode(s, "UTF-8")

    def uri(args: Any*): URI = {
      val sb = new StringBuilder

      val parts = sc.parts.iterator
      for (a <- args) {
        sb.append(parts.next())
        a match {
          case u: java.net.URI =>
            sb.append(u.toString)

          case u: java.net.URL =>
            sb.append(u.toString)

          case s: String =>
            sb.append(URLEncoder.encode(s, "UTF-8"))

          case x =>
            sb.append(URLEncoder.encode(x.toString, "UTF-8"))
        }
      }

      sb.append(parts.next())
      new URI(sb.result)
    }
  }
}
