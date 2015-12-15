package macros
import scala.language.experimental.macros
import scala.reflect.macros._

object Provider {
  def getMembers: String = macro getMembersImpl
  def getMembersImpl(c: Context): c.Tree = {
    import c.universe._
    val members = weakTypeOf[Foo].members.sorted
    q"${members.toString}"
  }
}