import sbt.internal.server.{ ServerHandler, ServerIntent }

ThisBuild / scalaVersion := "2.12.20"

Global / serverLog / logLevel := Level.Debug
// custom handler
Global / serverHandlers += ServerHandler({ callback =>
  import callback._
  import sjsonnew.BasicJsonProtocol._
  import sbt.internal.protocol.JsonRpcRequestMessage
  ServerIntent(
    onRequest = {
      case r: JsonRpcRequestMessage if r.method == "foo/export" =>
        appendExec(Exec("fooExport", Some(r.id), Some(CommandSource(callback.name))))
        ()
      case r: JsonRpcRequestMessage if r.method == "foo/fail" =>
        appendExec(Exec("fooFail", Some(r.id), Some(CommandSource(callback.name))))
        ()
      case r: JsonRpcRequestMessage if r.method == "foo/customfail" =>
        appendExec(Exec("fooCustomFail", Some(r.id), Some(CommandSource(callback.name))))
        ()
      case r: JsonRpcRequestMessage if r.method == "foo/notification" =>
        appendExec(Exec("fooNotification", Some(r.id), Some(CommandSource(callback.name))))
        ()
      case r: JsonRpcRequestMessage if r.method == "foo/rootClasspath" =>
        appendExec(Exec("fooClasspath", Some(r.id), Some(CommandSource(callback.name))))
        ()
      case r if r.method == "foo/respondTwice" =>
        appendExec(Exec("fooClasspath", Some(r.id), Some(CommandSource(callback.name))))
        jsonRpcRespond("concurrent response", Some(r.id))
        ()
      case r if r.method == "foo/resultAndError" =>
        appendExec(Exec("fooCustomFail", Some(r.id), Some(CommandSource(callback.name))))
        jsonRpcRespond("concurrent response", Some(r.id))
        ()
    },
    onResponse = PartialFunction.empty,
    onNotification = {
      case r if r.method == "foo/customNotification" =>
        jsonRpcRespond("notification result", None)
        ()
    }
  )
})

lazy val fooClasspath = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    name := "response",
    commands += Command.command("fooExport") { s0: State =>
      val (s1, cp) = s0.unsafeRunTask(Compile / fullClasspath)
      s0.respondEvent(cp.map(_.data))
      s1
    },
    commands += Command.command("fooFail") { s0: State =>
      sys.error("fail message")
    },
    commands += Command.command("fooCustomFail") { s0: State =>
      import sbt.internal.protocol.JsonRpcResponseError
      throw JsonRpcResponseError(500, "some error")
    },
    commands += Command.command("fooNotification") { s0: State =>
      import CacheImplicits._
      s0.notifyEvent("foo/something", "something")
      s0
    },
    fooClasspath := {
      val s = state.value
      val cp = (Compile / fullClasspath).value
      s.respondEvent(cp.map(_.data))
    }
  )
