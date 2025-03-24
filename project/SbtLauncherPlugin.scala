import sbt.Keys.*
import sbt.*
import sbt.io.CopyOptions

object SbtLauncherPlugin extends AutoPlugin {
  override def requires = plugins.IvyPlugin

  object autoImport {
    val SbtLaunchConfiguration = config("sbt-launch")
    val sbtLaunchJar = taskKey[File]("constructs an sbt-launch.jar for this version of sbt.")
    val rawSbtLaunchJar =
      taskKey[File]("The released version of the sbt-launcher we use to bundle this application.")
  }
  import autoImport.*

  override def projectConfigurations: Seq[Configuration] = Seq(SbtLaunchConfiguration)
  override def projectSettings: Seq[Setting[_]] = Seq(
    libraryDependencies += Dependencies.rawLauncher % SbtLaunchConfiguration.name,
    rawSbtLaunchJar := {
      Classpaths.managedJars(SbtLaunchConfiguration, Set("jar"), update.value).headOption match {
        case Some(jar) => jar.data
        case None =>
          sys.error(
            s"Could not resolve sbt launcher!, dependencies := ${libraryDependencies.value}"
          )
      }
    },
    sbtLaunchJar := {
      val propFiles = (Compile / resources).value
      val propFileLocations =
        for (file <- propFiles; if file.getName != "resources") yield {
          if (file.getName == "sbt.boot.properties") "sbt/sbt.boot.properties" -> file
          else file.getName -> file
        }
      // TODO - We need to inject the appropriate boot.properties file for this version of sbt.
      rebundle(rawSbtLaunchJar.value, propFileLocations.toMap, target.value / "sbt-launch.jar")
    }
  )

  def rebundle(jar: File, overrides: Map[String, File], target: File): File = {
    // TODO - Check if we should rebuild the jar or not....
    IO.withTemporaryDirectory { dir =>
      IO.unzip(jar, dir)
      IO.copy(overrides.map({ case (n, f) => (f, dir / n) }), CopyOptions().withOverwrite(true))
      // TODO - is the ok for creating a jar?
      val rebase: File => Seq[(File, String)] = {
        val path = dir.toPath
        f => if (f != dir) f -> path.relativize(f.toPath).toString :: Nil else Nil
      }
      IO.zip(dir.allPaths.get().flatMap(rebase), target, None)
    }
    target
  }

}
