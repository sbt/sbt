package example

import sbt.*
import Keys.*
import complete.DefaultParsers.{ *, given }

object FooPlugin extends AutoPlugin:
  override def requires = empty
  override def trigger = allRequirements

  lazy object autoImport:
    @transient
    lazy val foo = taskKey[Unit]("foo")
    lazy val check = inputKey[Unit]("check")

  import autoImport.*
  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    foo := println("foo"),
    check := {
      val args = spaceDelimited("<arg>").parsed
      assert(name.value.endsWith(args.head), s"${name.value} does not end with ${args.head}")
    },
  )
end FooPlugin
