package sbtw

case class LauncherOptions(
    help: Boolean = false,
    verbose: Boolean = false,
    debug: Boolean = false,
    version: Boolean = false,
    numericVersion: Boolean = false,
    scriptVersion: Boolean = false,
    shutdownAll: Boolean = false,
    allowEmpty: Boolean = false,
    client: Boolean = false,
    jvmClient: Boolean = false,
    noServer: Boolean = false,
    noColors: Boolean = false,
    noGlobal: Boolean = false,
    noShare: Boolean = false,
    noHideJdkWarnings: Boolean = false,
    debugInc: Boolean = false,
    timings: Boolean = false,
    traces: Boolean = false,
    batch: Boolean = false,
    sbtDir: Option[String] = None,
    sbtBoot: Option[String] = None,
    sbtCache: Option[String] = None,
    sbtJar: Option[String] = None,
    sbtVersion: Option[String] = None,
    ivy: Option[String] = None,
    mem: Option[Int] = None,
    supershell: Option[String] = None,
    color: Option[String] = None,
    autostart: Option[String] = None,
    jvmDebug: Option[Int] = None,
    javaHome: Option[String] = None,
    server: Boolean = false,
    residual: Seq[String] = Nil,
    sbtNew: Boolean = false,
)

object LauncherOptions:
  val defaultMemMb = 1024
  val initSbtVersion = "_to_be_replaced"
end LauncherOptions
