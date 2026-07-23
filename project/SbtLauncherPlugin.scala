import sbt.Keys.*
import sbt.*
import sbt.io.CopyOptions
import sbt.util.CacheImplicits.given

object SbtLauncherPlugin extends AutoPlugin {
  override def requires = plugins.IvyPlugin

  object autoImport {
    val SbtLaunchConfiguration = config("sbt-launch")
    val sbtLaunchJar =
      taskKey[HashedVirtualFileRef]("constructs an sbt-launch.jar for this version of sbt.")
    val rawSbtLaunchJar =
      taskKey[HashedVirtualFileRef](
        "The released version of the sbt-launcher we use to bundle this application."
      )
  }
  import autoImport.*

  override def projectConfigurations: Seq[Configuration] = Seq(SbtLaunchConfiguration)
  override def projectSettings: Seq[Setting[?]] = Seq(
    libraryDependencies += Dependencies.rawLauncher % SbtLaunchConfiguration.name,
    rawSbtLaunchJar := Def.uncached {
      val converter = fileConverter.value
      Classpaths
        .managedJars(SbtLaunchConfiguration, Set("jar"), update.value, converter)
        .headOption match {
        case Some(jar) => jar.data
        case None      =>
          sys.error(
            s"Could not resolve sbt launcher!, dependencies := ${libraryDependencies.value}"
          )
      }
    },
    sbtLaunchJar := Def.uncached {
      val propFiles = (Compile / resources).value
      val propFileLocations =
        for (file <- propFiles; if file.getName != "resources") yield {
          if (file.getName == "sbt.boot.properties") "sbt/sbt.boot.properties" -> file
          else file.getName -> file
        }
      // TODO - We need to inject the appropriate boot.properties file for this version of sbt.
      fileConverter.value.toVirtualFile(
        rebundle(
          fileConverter.value.toPath(rawSbtLaunchJar.value).toFile,
          propFileLocations.toMap,
          target.value / "sbt-launch.jar"
        ).toPath
      )
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
