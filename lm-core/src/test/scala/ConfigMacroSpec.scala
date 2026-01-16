package sbt.internal.librarymanagement

import sbt.librarymanagement.Configuration
import sbt.librarymanagement.Configurations.config
import scala.util.control.NonFatal
import org.scalacheck.*
import Prop.*

class ConfigDefs {
  lazy val Kompile = config("kompile")
  val X = config("x")
  val Z = config("z").hide
  val A: Configuration = config("a")
  lazy val Aa: Configuration = config("aa")
}

object ConfigMacroSpec extends Properties("ConfigMacroSpec") {
  lazy val cd = new ConfigDefs
  import cd.*

  def secure(f: => Prop): Prop =
    try {
      Prop.secure(f)
    } catch {
      case NonFatal(e) =>
        e.printStackTrace
        throw e
    }

  property("Explicit type on lazy val supported") = secure {
    check(Aa, "Aa", "aa", true)
  }

  property("Explicit type on val supported") = secure {
    check(A, "A", "a", true)
  }

  property("lazy vals supported") = secure {
    check(Kompile, "Kompile", "kompile", true)
  }

  property("plain vals supported") = secure {
    check(X, "X", "x", true)
  }

  property("Directory overridable") = secure {
    check(Z, "Z", "z", false)
  }

  def check(c: Configuration, id: String, name: String, isPublic: Boolean): Prop = {
    s"Expected id: $id" |:
      s"Expected name: $name" |:
      s"Expected isPublic: $isPublic" |:
      s"Actual id: ${c.id}" |:
      s"Actual name: ${c.name}" |:
      s"Actual isPublic: ${c.isPublic}" |:
      (c.id == id) &&
      (c.name == name) &&
      (c.isPublic == isPublic)
  }
}
