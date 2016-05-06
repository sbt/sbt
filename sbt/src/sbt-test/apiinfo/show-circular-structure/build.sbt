logLevel := Level.Debug

incOptions ~= { _.withApiDebug(true) }

TaskKey[Unit]("show-apis") <<= (compile in Compile, scalaSource in Compile, javaSource in Compile) map { case (a: sbt.internal.inc.Analysis, scalaSrc: java.io.File, javaSrc: java.io.File) =>
  val aApi = a.apis.internalAPI("test.A").api.classApi
  val jApi = a.apis.internalAPI("test.J").api.classApi
  import xsbt.api.DefaultShowAPI
  import DefaultShowAPI._
  DefaultShowAPI(aApi)
  DefaultShowAPI(jApi)
}
