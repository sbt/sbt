import scala.language.experimental.macros
import scala.reflect.macros._

object Provider {
  def macroWithAuxFile: String = macro macroWithAuxFileImpl
  def macroWithAuxFileImpl(c: Context): c.Tree = {
    import c.universe._, compat._

    val file = new java.io.File("util/util.txt")
    val lines = scala.io.Source.fromFile(file).mkString
    val map = new java.util.HashMap[String, Any]
    map.put("touchedFiles", file :: Nil)

    val tree = q"$lines"
    tree.updateAttachment(map)
  }
}
