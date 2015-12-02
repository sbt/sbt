logLevel := Level.Debug

incOptions ~= { _.copy(apiDebug = true) }

TaskKey[Unit]("show-apis") <<= (compile in Compile, scalaSource in Compile, javaSource in Compile) map { (a: sbt.inc.Analysis, scalaSrc: java.io.File, javaSrc: java.io.File) =>
  val aApi = a.apis.internalAPI(scalaSrc / "A.scala").api
  val jApi = a.apis.internalAPI(javaSrc / "test/J.java").api
  import xsbt.api.DefaultShowAPI
  DefaultShowAPI(aApi)
  DefaultShowAPI(jApi)
}
