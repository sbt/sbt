import sbt.internal.server.{ ServerHandler, ServerIntent }

ThisBuild / scalaVersion := "2.12.10"

lazy val root = (project in file("."))
  .settings(
    Global / serverLog / logLevel := Level.Debug,

    // custom handler
    Global / serverHandlers += ServerHandler({ callback =>
      import callback._
      import sjsonnew.BasicJsonProtocol._
      import sbt.internal.protocol.JsonRpcRequestMessage
      ServerIntent(
        {
          case r: JsonRpcRequestMessage if r.method == "lunar/helo" =>
            jsonRpcNotify("lunar/oleh", "")
            ()
        },
        PartialFunction.empty
      )
    }),

    name := "handshake",
  )
