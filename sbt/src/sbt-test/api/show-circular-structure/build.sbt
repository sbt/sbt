import sbt.inc.Analysis

logLevel := Level.Debug

incOptions ~= { _.withApiDebug(true) }

TaskKey[Unit]("show-apis") := {
  val a = (compile in Compile).value match { case a: Analysis => a }
  val scalaSrc = (scalaSource in Compile).value
  val javaSrc = (javaSource in Compile).value
  val aApi = a.apis.internalAPI(scalaSrc / "A.scala").api
  val jApi = a.apis.internalAPI(javaSrc / "test/J.java").api
  import xsbt.api.DefaultShowAPI
  DefaultShowAPI(aApi)
  DefaultShowAPI(jApi)
}
