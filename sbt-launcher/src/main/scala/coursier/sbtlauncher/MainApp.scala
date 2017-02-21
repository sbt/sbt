package coursier.sbtlauncher

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import caseapp._
import com.typesafe.config.ConfigFactory
import coursier.Dependency

final case class MainApp(
  @ExtraName("org")
    organization: String,
  name: String,
  version: String,
  scalaVersion: String,
  sbtVersion: String,
  mainClass: String,
  mainComponents: List[String],
  classpathExtra: List[String],
  extra: List[String]
) extends App {

  val sbtPropFile = new File(sys.props("user.dir") + "/sbt.properties")
  val buildPropFile = new File(sys.props("user.dir") + "/project/build.properties")

  val propFileOpt = Some(sbtPropFile).filter(_.exists())
    .orElse(Some(buildPropFile).filter(_.exists()))

  val (org0, name0, ver0, scalaVer0, extraDeps0, mainClass0, sbtVersion0) =
    propFileOpt match {
      case Some(propFile) =>
        // can't get ConfigFactory.parseFile to work fine here
        val conf = ConfigFactory.parseString(new String(Files.readAllBytes(propFile.toPath), StandardCharsets.UTF_8))
          .withFallback(ConfigFactory.defaultReference(Thread.currentThread().getContextClassLoader))
          .resolve()
        val sbtConfig = SbtConfig.fromConfig(conf)

        (sbtConfig.organization, sbtConfig.moduleName, sbtConfig.version, sbtConfig.scalaVersion, sbtConfig.dependencies, sbtConfig.mainClass, sbtConfig.version)
      case None =>
        require(scalaVersion.nonEmpty, "No scala version specified")
        (organization, name, version, scalaVersion, Nil, mainClass, sbtVersion)
    }

  val (extraParseErrors, extraModuleVersions) = coursier.util.Parse.moduleVersions(extra, scalaVersion)

  if (extraParseErrors.nonEmpty) {
    ???
  }

  val extraDeps = extraModuleVersions.map {
    case (mod, ver) =>
      Dependency(mod, ver)
  }

  val launcher = new Launcher(
    scalaVer0,
    // FIXME Add org & moduleName in this path
    new File(s"${sys.props("user.dir")}/target/sbt-components/components_scala$scalaVer0${if (sbtVersion0.isEmpty) "" else "_sbt" + sbtVersion0}"),
    new File(s"${sys.props("user.dir")}/target/ivy2")
  )

  launcher.registerScalaComponents()

  if (sbtVersion0.nonEmpty)
    launcher.registerSbtInterfaceComponents(sbtVersion0)

  val appId = ApplicationID(
    org0,
    name0,
    ver0,
    mainClass0,
    mainComponents.toArray,
    crossVersioned = false,
    xsbti.CrossValue.Disabled,
    classpathExtra.map(new File(_)).toArray
  )

  val appProvider = launcher.app(appId, extraDeps0 ++ extraDeps: _*)

  val appMain = appProvider.newMain()

  val appConfig = AppConfiguration(
    remainingArgs.toArray,
    new File(sys.props("user.dir")),
    appProvider
  )

  val thread = Thread.currentThread()
  val previousLoader = thread.getContextClassLoader

  val result =
    try {
      thread.setContextClassLoader(appProvider.loader())
      appMain.run(appConfig)
    } finally {
      thread.setContextClassLoader(previousLoader)
    }

  result match {
    case _: xsbti.Continue =>
    case e: xsbti.Exit =>
      sys.exit(e.code())
    case _: xsbti.Reboot =>
      sys.error("Not able to reboot yet")
  }

}

object Main extends AppOf[MainApp]
