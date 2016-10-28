import scala.util.control.Exception.catching
import _root_.bintray.InternalBintrayKeys._
import _root_.bintray.{BintrayRepo, Bintray}

lazy val sbtVersionToRelease = sys.props.getOrElse("sbt.build.version", sys.env.getOrElse("sbt.build.version", {
        sys.error("-Dsbt.build.version must be set")
      }))
lazy val isExperimental = (sbtVersionToRelease contains "RC") || (sbtVersionToRelease contains "M")
val sbtLaunchJarUrl = SettingKey[String]("sbt-launch-jar-url")
val sbtLaunchJarLocation = SettingKey[File]("sbt-launch-jar-location")  
val sbtLaunchJar = TaskKey[File]("sbt-launch-jar", "Resolves SBT launch jar")
val moduleID = (organization) apply { (o) => ModuleID(o, "sbt", sbtVersionToRelease) }

val bintrayLinuxPattern = "[module]/[revision]/[module]-[revision].[ext]"
val bintrayGenericPattern = "[module]/[revision]/[module]/[revision]/[module]-[revision].[ext]"
val bintrayDebianUrl = "https://api.bintray.com/content/sbt/debian/"
val bintrayDebianExperimentalUrl = "https://api.bintray.com/content/sbt/debian-experimental/"
val bintrayRpmUrl = "https://api.bintray.com/content/sbt/rpm/"
val bintrayRpmExperimentalUrl = "https://api.bintray.com/content/sbt/rpm-experimental/"
val bintrayGenericPackagesUrl = "https://api.bintray.com/content/sbt/native-packages/"
val bintrayReleaseAllStaged = TaskKey[Unit]("bintray-release-all-staged", "Release all staged artifacts on bintray.")
val windowsBuildId = settingKey[Int]("build id for Windows installer")

// This build creates a SBT plugin with handy features *and* bundles the SBT script for distribution.
val root = (project in file(".")).
  enablePlugins(UniversalPlugin, LinuxPlugin, DebianPlugin, RpmPlugin, WindowsPlugin).
  settings(
    organization := "org.scala-sbt",
    name := "sbt-launcher-packaging",
    version := "0.1.0",
    crossTarget <<= target,
    deploymentSettings,
    publishToSettings,
    sbtLaunchJarUrl := downloadUrlForVersion(sbtVersionToRelease),
    sbtLaunchJarLocation <<= target apply (_ / "sbt-launch.jar"),
    sbtLaunchJar <<= (sbtLaunchJarUrl, sbtLaunchJarLocation) map { (uri, file) =>
      import dispatch.classic._
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
    maintainer := "Eugene Yokota <eugene.yokota@lightbend.com>",
    packageSummary := "sbt, the interactive build tool",
    packageDescription := """This script provides a native way to run sbt,
  a build tool for Scala and more.""",
    // Here we remove the jar file and launch lib from the symlinks:
    linuxPackageSymlinks <<= linuxPackageSymlinks map { links =>
      for {
        link <- links
        if !(link.destination endsWith "sbt-launch-lib.bash")
        if !(link.destination endsWith "sbt-launch.jar")
      } yield link
    },
    // DEBIAN SPECIFIC
    name in Debian := "sbt",
    version in Debian := sbtVersionToRelease,
    debianPackageDependencies in Debian ++= Seq("java6-runtime-headless", "bash (>= 2.05a-11)"),
    debianPackageRecommends in Debian += "git",
    linuxPackageMappings in Debian <+= (sourceDirectory) map { bd =>
      (packageMapping(
        (bd / "debian/changelog") -> "/usr/share/doc/sbt/changelog.gz"
      ) withUser "root" withGroup "root" withPerms "0644" gzipped) asDocs()
    },
    debianChangelog in Debian := Some(file("debian/changelog"))

    // RPM SPECIFIC
    name in Rpm := "sbt",
    version in Rpm := {
      val stable = (sbtVersionToRelease split "[^\\d]" filterNot (_.isEmpty) mkString ".")
      if (isExperimental) ((sbtVersionToRelease split "[^\\d]" filterNot (_.isEmpty)).toList match {
        case List(a, b, c, d) => List(0, 99, c, d).mkString(".")
      })
      else stable
    },
    rpmRelease := "1",
    rpmVendor := "typesafe",
    rpmUrl := Some("http://github.com/sbt/sbt-launcher-package"),
    rpmLicense := Some("BSD"),
    rpmRequirements :=Seq("java","java-devel","jpackage-utils"),
    rpmProvides := Seq("sbt"),

    // WINDOWS SPECIFIC
    name in Windows := "sbt",
    windowsBuildId := 1,
    version in Windows <<= (windowsBuildId) apply { (bid) =>
      val sv = sbtVersionToRelease
      (sv split "[^\\d]" filterNot (_.isEmpty)) match {
        case Array(major,minor,bugfix, _*) => Seq(major, minor, bugfix, bid.toString) mkString "."
        case Array(major,minor) => Seq(major, minor, "0", bid.toString) mkString "."
        case Array(major) => Seq(major, "0", "0", bid.toString) mkString "."
      }
    },
    maintainer in Windows := "Lightbend, Inc.",
    packageSummary in Windows := "sbt " + (version in Windows).value,
    packageDescription in Windows := "The interactive build tool.",
    wixProductId := "ce07be71-510d-414a-92d4-dff47631848a",
    wixProductUpgradeId := Hash.toHex(Hash((version in Windows).value)).take(32),
    javacOptions := Seq("-source", "1.5", "-target", "1.5"),

    // Universal ZIP download install.
    name in Universal := "sbt",
    version in Universal := sbtVersionToRelease,
    mappings in Universal <+= sbtLaunchJar map { _ -> "bin/sbt-launch.jar" },

    // Misccelaneous publishing stuff...
    projectID in Debian    <<= moduleID,
    projectID in Windows := {
      val m = moduleID.value
      m.copy(revision = (version in Windows).value)
    },
    projectID in Rpm       <<= moduleID,
    projectID in Universal <<= moduleID
  )

def downloadUrlForVersion(v: String) = (v split "[^\\d]" flatMap (i => catching(classOf[Exception]) opt (i.toInt))) match {
  case Array(0, 11, 3, _*)           => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.11.3-2/sbt-launch.jar"
  case Array(0, 11, x, _*) if x >= 3 => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case Array(0, y, _*) if y >= 12    => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case Array(1, _, _*)               => "http://repo.scala-sbt.org/scalasbt/maven-releases/org/scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case _                             => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-tools.sbt/sbt-launch/"+v+"/sbt-launch.jar"
}

def makePublishTo(id: String, url: String, pattern: String): Setting[_] = {
  publishTo := {
    val resolver = Resolver.url(id, new URL(url))(Patterns(pattern))
    Some(resolver)
  }
}

def makePublishToForConfig(config: Configuration) = {
  val v = sbtVersionToRelease
  val (id, url, pattern) =
    config.name match {
      case Debian.name if isExperimental => ("debian-experimental", bintrayDebianExperimentalUrl, bintrayLinuxPattern)
      case Debian.name => ("debian", bintrayDebianUrl, bintrayLinuxPattern)
      case Rpm.name if isExperimental => ("rpm-experimental", bintrayRpmExperimentalUrl, bintrayLinuxPattern)
      case Rpm.name    => ("rpm", bintrayRpmUrl, bintrayLinuxPattern)
      case _           => ("native-packages", bintrayGenericPackagesUrl, bintrayGenericPattern)
    }
  // Add the publish to and ensure global resolvers has the resolver we just configured.
  inConfig(config)(Seq(
    bintrayOrganization := Some("sbt"),
    bintrayRepository := id,
    bintrayRepo := Bintray.cachedRepo(bintrayEnsureCredentials.value,
      bintrayOrganization.value,
      bintrayRepository.value),
    bintrayPackage := "sbt",
    makePublishTo(id, url, pattern),
    bintrayReleaseAllStaged := bintrayRelease(bintrayRepo.value, bintrayPackage.value, version.value, sLog.value)
    // Uncomment to release right after publishing
    // publish <<= (publish, bintrayRepo, bintrayPackage, version, sLog) apply { (publish, bintrayRepo, bintrayPackage, version, sLog) =>
    //   for {
    //     pub <- publish
    //     repo <- bintrayRepo
    //   } yield bintrayRelease(repo, bintrayPackage, version, sLog)
    // }
  )) ++ Seq(
     resolvers <++= (publishTo in config) apply (_.toSeq)
  )
}

def publishToSettings =
  Seq[Configuration](Debian, Universal, Windows, Rpm) flatMap makePublishToForConfig

def bintrayRelease(repo: BintrayRepo, pkg: String, version: String, log: Logger): Unit =
  repo.release(pkg, version, log)

