import sbt.internal.inc.Analysis

logLevel := Level.Debug

incOptions ~= { _.withApiDebug(true) }

TaskKey[Unit]("show-apis") := {
  val a = (compile in Compile).value match { case a: Analysis => a }
  val scalaSrc = (scalaSource in Compile).value
  val javaSrc = (javaSource in Compile).value
  val aApi = a.apis.internalAPI("test.A").api.classApi
  val jApi = a.apis.internalAPI("test.J").api.classApi
  import xsbt.api.DefaultShowAPI
  import DefaultShowAPI._
  DefaultShowAPI(aApi)
  DefaultShowAPI(jApi)
}
