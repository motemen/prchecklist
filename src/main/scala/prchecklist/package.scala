import org.json4s.native.{ Serialization => JsonSerialization }
import org.json4s.NoTypeHints

package object prchecklist {

  implicit def jsonFormats = JsonSerialization.formats(NoTypeHints)
}
