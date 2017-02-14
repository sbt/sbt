import sbt._, Keys._

import sbt.internal.SessionSettings

object Common {
  lazy val k1 = taskKey[Unit]("")
  lazy val k2 = taskKey[Unit]("")
  lazy val k3 = taskKey[Unit]("")
  lazy val k4 = taskKey[Unit]("")

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


  val UpdateK3 = Command.command("UpdateK3") { st: State =>
    val ex = Project extract st
    import ex._
    val session2 = BuiltinCommands.setThis(st, ex, Seq(k3 := {}), """k3 := {
                                                                    |//
                                                                    |//
                                                                    |}""".stripMargin).session
    val st1 = BuiltinCommands.reapply(session2, structure, st)
    // SessionSettings.writeSettings(ex.currentRef, session2, ex.session.original, ex.structure)
    SessionSettings.saveAllSettings(st1)
  }
}

// vim: set ts=4 sw=4 et:
