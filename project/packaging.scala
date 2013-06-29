import sbt._
import com.typesafe.sbt.packager.Keys._
import sbt.Keys._
import com.typesafe.sbt.SbtNativePackager._

object Packaging {

  val sbtLaunchJarUrl = SettingKey[String]("sbt-launch-jar-url")
  val sbtLaunchJarLocation = SettingKey[File]("sbt-launch-jar-location")  
  val sbtLaunchJar = TaskKey[File]("sbt-launch-jar", "Resolves SBT launch jar")

  val stagingDirectory = SettingKey[File]("staging-directory")
  val stage = TaskKey[File]("stage")

  def localWindowsPattern = "[organisation]/[module]/[revision]/[module].[ext]"

  import util.control.Exception.catching  

  def downloadUrlForVersion(v: String) = (v split "[^\\d]" flatMap (i => catching(classOf[Exception]) opt (i.toInt))) match {
    case Array(0, 11, 3, _*)           => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.11.3-2/sbt-launch.jar"
    case Array(0, 11, x, _*) if x >= 3 => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
    case Array(0, y, _*) if y >= 12    => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
    case _                             => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-tools.sbt/sbt-launch/"+v+"/sbt-launch.jar"
  }

  val settings: Seq[Setting[_]] = packagerSettings ++ deploymentSettings ++ mapGenericFilesToLinux ++ mapGenericFilesToWinows ++ Seq(
    sbtLaunchJarUrl <<= sbtVersion apply downloadUrlForVersion,
    sbtLaunchJarLocation <<= target apply (_ / "sbt-launch.jar"),
    sbtLaunchJar <<= (sbtLaunchJarUrl, sbtLaunchJarLocation) map { (uri, file) =>
      import dispatch._
      if(!file.exists) {
         // oddly, some places require us to create the file before writing...
         IO.touch(file)
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(file))
         try Http(url(uri) >>> writer)
         finally writer.close()
      }
      // TODO - GPG Trust validation.
      file
    },
    // GENERAL LINUX PACKAGING STUFFS
    maintainer := "Josh Suereth <joshua.suereth@typesafe.com>",
    packageSummary := "Simple Build Tool for Scala-driven builds",
    packageDescription := """This script provides a native way to run the Simple Build Tool,
  a build tool for Scala software, also called SBT.""",
    // Here we remove the jar file and launch lib from the symlinks:
    linuxPackageSymlinks <<= linuxPackageSymlinks map { links =>
      for { 
        link <- links
        if !(link.destination endsWith "sbt-launch-lib.bash")
        if !(link.destination endsWith "sbt-launch.jar")
      } yield link
    },
    // DEBIAN SPECIFIC    
    name in Debian <<= (sbtVersion) apply { (sv) => "sbt" /* + "-" + (sv split "[^\\d]" take 3 mkString ".")*/ },
    version in Debian <<= (version, sbtVersion) apply { (v, sv) =>
      val nums = (v split "[^\\d]")
      "%s-%s-build-%03d" format (sv, (nums.init mkString "."), nums.last.toInt + 1)
    },
    debianPackageDependencies in Debian ++= Seq("curl", "java2-runtime", "bash (>= 2.05a-11)"),
    debianPackageRecommends in Debian += "git",
    linuxPackageMappings in Debian <+= (sourceDirectory) map { bd =>
      (packageMapping(
        (bd / "debian/changelog") -> "/usr/share/doc/sbt/changelog.gz"
      ) withUser "root" withGroup "root" withPerms "0644" gzipped) asDocs()
    },
    
    // RPM SPECIFIC
    name in Rpm := "sbt",
    version in Rpm <<= sbtVersion apply { sv => (sv split "[^\\d]" filterNot (_.isEmpty) mkString ".") },
    rpmRelease := "1",
    rpmVendor := "typesafe",
    rpmUrl := Some("http://github.com/paulp/sbt-extras"),
    rpmLicense := Some("BSD"),
    
    
    // WINDOWS SPECIFIC
    name in Windows := "sbt",
    version in Windows <<= (sbtVersion) apply { sv =>
      (sv split "[^\\d]" filterNot (_.isEmpty)) match {
        case Array(major,minor,bugfix, _*) => Seq(major,minor,bugfix, "1") mkString "."
        case Array(major,minor) => Seq(major,minor,"0","1") mkString "."
        case Array(major) => Seq(major,"0","0","1") mkString "."
      }
    },
    maintainer in Windows := "Typesafe, Inc.",
    packageSummary in Windows := "Simple Build Tool",
    packageDescription in Windows := "THE reactive build tool.",
    wixProductId := "ce07be71-510d-414a-92d4-dff47631848a",
    wixProductUpgradeId := "4552fb0e-e257-4dbd-9ecb-dba9dbacf424",
    javacOptions := Seq("-source", "1.5", "-target", "1.5"),

    // Universal ZIP download install.
    name in Universal := "sbt",
    mappings in Universal <+= sbtLaunchJar map { _ -> "bin/sbt-launch.jar" },
    
    // Misccelaneous publishing stuff...
    projectID in Debian    <<= (organization, sbtVersion) apply { (o,v) => ModuleID(o,"sbt",v) },
    projectID in Windows   <<= (organization, sbtVersion) apply { (o,v) => ModuleID(o,"sbt",v) },
    projectID in Rpm       <<= (organization, sbtVersion) apply { (o,v) => ModuleID(o,"sbt",v) },
    projectID in Universal <<= (organization, sbtVersion) apply { (o,v) => ModuleID(o,"sbt",v) },
    stagingDirectory <<= (target) apply { (t) => t / "stage" },
    stage <<= (stagingDirectory, mappings in Universal) map { (dir, m) =>
      val files = for((file, name) <- m)
                  yield file -> (dir / name)
      IO copy files
      dir
    }
  )
}
