import sbt._
import Keys._

object build extends Build {
  lazy val k1 = taskKey[Unit]("")
  lazy val k2 = taskKey[Unit]("")

  val UpdateK1 = Command.command("UpdateK1") { st: State =>
    val ex = Project extract st
    import ex._
    val session2 = BuiltinCommands.setThis(st, ex, Seq(k1 := {}), """k1 := {
    |//
    |//
    |}""".stripMargin).session
    val st1 = BuiltinCommands.reapply(session2, structure, st)
    // SessionSettings.writeSettings(ex.currentRef, session2, ex.session.original, ex.structure)
    SessionSettings.saveAllSettings(st1)
  }

	lazy val root = Project("root", file(".")) settings(
    commands += UpdateK1
  )
}

// vim: set ts=4 sw=4 et:
