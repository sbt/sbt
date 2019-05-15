import scala.util.control.Exception.catching
import _root_.bintray.InternalBintrayKeys._
import _root_.bintray.{BintrayRepo, Bintray}
import NativePackagerHelper._
import com.typesafe.sbt.packager.SettingsHelper._
import DebianConstants._

lazy val sbtOfflineInstall =
  sys.props.getOrElse("sbt.build.offline", sys.env.getOrElse("sbt.build.offline", "true")) match {
    case "true" | "1"  => true
    case "false" | "0" => false
    case _             => false
  }
lazy val sbtVersionToRelease = sys.props.getOrElse("sbt.build.version", sys.env.getOrElse("sbt.build.version", {
        sys.error("-Dsbt.build.version must be set")
      }))

lazy val scala210 = "2.10.7"
lazy val scala212 = "2.12.8"
lazy val scala210Jline = "org.scala-lang" % "jline" % scala210
lazy val jansi = {
  if (sbtVersionToRelease startsWith "1.") "org.fusesource.jansi" % "jansi" % "1.4"
  else "org.fusesource.jansi" % "jansi" % "1.4"
}
lazy val scala212Jline = "jline" % "jline" % "2.14.6"
lazy val scala212Xml = "org.scala-lang.modules" % "scala-xml_2.12" % "1.0.6"
lazy val scala212Compiler = "org.scala-lang" % "scala-compiler" % scala212
lazy val sbtActual = "org.scala-sbt" % "sbt" % sbtVersionToRelease

lazy val sbt013ExtraDeps = {
  if (sbtVersionToRelease startsWith "0.13.") Seq(scala210Jline)
  else Seq()
}

lazy val isWindows: Boolean = sys.props("os.name").toLowerCase(java.util.Locale.ENGLISH).contains("windows")
lazy val isExperimental = (sbtVersionToRelease contains "RC") || (sbtVersionToRelease contains "M")
val sbtLaunchJarUrl = SettingKey[String]("sbt-launch-jar-url")
val sbtLaunchJarLocation = SettingKey[File]("sbt-launch-jar-location")
val sbtLaunchJar = TaskKey[File]("sbt-launch-jar", "Resolves SBT launch jar")
val moduleID = (organization) apply { (o) => ModuleID(o, "sbt", sbtVersionToRelease) }

lazy val bintrayDebianUrl = settingKey[String]("API point for Debian packages")
lazy val bintrayDebianExperimentalUrl = settingKey[String]("API point for Debian experimental packages")
lazy val bintrayRpmUrl = settingKey[String]("API point for RPM packages")
lazy val bintrayRpmExperimentalUrl = settingKey[String]("API point for RPM experimental packages")
lazy val bintrayGenericPackagesUrl = settingKey[String]("API point for generic packages")
lazy val bintrayTripple = settingKey[(String, String, String)]("id, url, and pattern")

val bintrayLinuxPattern = "[module]/[revision]/[module]-[revision].[ext]"
val bintrayGenericPattern = "[module]/[revision]/[module]/[revision]/[module]-[revision].[ext]"
val bintrayReleaseAllStaged = TaskKey[Unit]("bintray-release-all-staged", "Release all staged artifacts on bintray.")
val windowsBuildId = settingKey[Int]("build id for Windows installer")
val debianBuildId = settingKey[Int]("build id for Debian")

val exportRepoUsingCoursier = taskKey[File]("export Maven style repository")
val exportRepoCsrDirectory = settingKey[File]("")

// This build creates a SBT plugin with handy features *and* bundles the SBT script for distribution.
val root = (project in file(".")).
  enablePlugins(UniversalPlugin, LinuxPlugin, DebianPlugin, RpmPlugin, WindowsPlugin,
    UniversalDeployPlugin, DebianDeployPlugin, RpmDeployPlugin, WindowsDeployPlugin).
  settings(
    organization := "org.scala-sbt",
    name := "sbt-launcher-packaging",
    packageName := "sbt",
    version := "0.1.0",
    crossTarget := target.value,
    clean := {
      val _ = (clean in dist).value
      clean.value
    },
    useGpg := true,
    usePgpKeyHex("642AC823"),
    pgpSecretRing := file(s"""${sys.props("user.home")}""") / ".ssh" / "scalasbt.key",
    pgpPublicRing := file(s"""${sys.props("user.home")}""") / ".ssh" / "scalasbt.pub",
    publishToSettings,
    sbtLaunchJarUrl := downloadUrlForVersion(sbtVersionToRelease),
    sbtLaunchJarLocation := { target.value / "sbt-launch.jar" },
    sbtLaunchJar := {
      val uri = sbtLaunchJarUrl.value
      val file = sbtLaunchJarLocation.value
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
    linuxPackageSymlinks := {
      val links = linuxPackageSymlinks.value
      for {
        link <- links
        if !(link.destination endsWith "sbt-launch.jar")
      } yield link
    },

    // DEBIAN SPECIFIC
    debianBuildId := 0,
    version in Debian := {
      if (debianBuildId.value == 0) sbtVersionToRelease
      else sbtVersionToRelease + "." + debianBuildId.value
    },
    // Used to have "openjdk-8-jdk" but that doesn't work on Ubuntu 14.04 https://github.com/sbt/sbt/issues/3105
    // before that we had java6-runtime-headless" and that was pulling in JDK9 on Ubuntu 16.04 https://github.com/sbt/sbt/issues/2931
    debianPackageDependencies in Debian ++= Seq("bash (>= 3.2)"),
    debianPackageRecommends in Debian += "git",
    linuxPackageMappings in Debian += {
      val bd = sourceDirectory.value
      (packageMapping(
        (bd / "debian" / "changelog") -> "/usr/share/doc/sbt/changelog.gz"
      ) withUser "root" withGroup "root" withPerms "0644" gzipped) asDocs()
    },
    debianChangelog in Debian := { Some(sourceDirectory.value / "debian" / "changelog") },
    addPackage(Debian, packageBin in Debian, "deb"),
    debianNativeBuildOptions in Debian := Seq("-Zgzip", "-z3"),

    // RPM SPECIFIC
    rpmRelease := "0",
    version in Rpm := {
      val stable0 = (sbtVersionToRelease split "[^\\d]" filterNot (_.isEmpty) mkString ".")
      val stable = if (rpmRelease.value == "0") stable0
                   else stable0 + "." + rpmRelease.value
      if (isExperimental) ((sbtVersionToRelease split "[^\\d]" filterNot (_.isEmpty)).toList match {
        case List(a, b, c, d) => List(0, 99, c, d).mkString(".")
      })
      else stable
    },
    rpmVendor := "lightbend",
    rpmUrl := Some("http://github.com/sbt/sbt-launcher-package"),
    rpmLicense := Some("BSD"),
    // This is intentionally empty. java-devel could bring in JDK 9-ea on Fedora,
    // and java-1.8.0-devel doesn't work on CentOS 6 and 7.
    // https://github.com/sbt/sbt-launcher-package/issues/151
    // https://github.com/elastic/logstash/issues/6275#issuecomment-261359933
    rpmRequirements := Seq(),
    rpmProvides := Seq("sbt"),

    // WINDOWS SPECIFIC
    windowsBuildId := 0,
    version in Windows := {
      val bid = windowsBuildId.value
      val sv = sbtVersionToRelease
      (sv split "[^\\d]" filterNot (_.isEmpty)) match {
        case Array(major,minor,bugfix, _*) if bid == 0 => Seq(major, minor, bugfix) mkString "."
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
    packageName in Universal := packageName.value, // needs to be set explicitly due to a bug in native-packager
    version in Universal := sbtVersionToRelease,

    mappings in Universal := {
      val t = (target in Universal).value
      val prev = (mappings in Universal).value
      val BinBat = "bin" + java.io.File.separator + "sbt.bat"
      prev.toList map {
        case (k, BinBat) =>
          val x = IO.read(k)
          IO.write(t / "sbt.bat", x.replaceAllLiterally(
            "set INIT_SBT_VERSION=_TO_BE_REPLACED",
            s"""set INIT_SBT_VERSION=$sbtVersionToRelease"""))
          (t / "sbt.bat", BinBat)
        case (k, v) => (k, v)
      }
    },

    mappings in Universal ++= {
      val launchJar = sbtLaunchJar.value
      val rtExportJar = (packageBin in Compile in java9rtexport).value
      Seq(launchJar -> "bin/sbt-launch.jar", rtExportJar -> "bin/java9-rt-export.jar")
    },
    mappings in Universal ++= (Def.taskDyn {
      if (sbtOfflineInstall && sbtVersionToRelease.startsWith("1."))
        Def.task {
          val _ = (exportRepoUsingCoursier in dist).value
          directory((target in dist).value / "lib")
        }
      else if (sbtOfflineInstall)
        Def.task {
          val _ = (exportRepo in dist).value
          directory((target in dist).value / "lib")
        }
      else Def.task { Seq[(File, String)]() }
    }).value,
    mappings in Universal ++= {
      val base = baseDirectory.value
      if (sbtVersionToRelease startsWith "0.13.") Nil
      else Seq[(File, String)](base / "LICENSE" -> "LICENSE", base / "NOTICE" -> "NOTICE")
    },

    // Misccelaneous publishing stuff...
    projectID in Debian := {
      val m = moduleID.value
      m.copy(revision = (version in Debian).value)
    },
    projectID in Windows := {
      val m = moduleID.value
      m.copy(revision = (version in Windows).value)
    },
    projectID in Rpm := {
      val m = moduleID.value
      m.copy(revision = (version in Rpm).value)
    },
    projectID in Universal := {
      val m = moduleID.value
      m.copy(revision = (version in Universal).value)
    }
  )

lazy val integrationTest = (project in file("integration-test"))
  .settings(
    name := "integration-test",
    scalaVersion := "2.12.8",
    libraryDependencies ++= Seq(
      "io.monix" %% "minitest" % "2.3.2" % Test,
      "com.eed3si9n.expecty" %% "expecty" % "0.11.0" % Test,
      "org.scala-sbt" %% "io" % "1.2.2" % Test
    ),
    testFrameworks += new TestFramework("minitest.runner.Framework")
  )

lazy val java9rtexport = (project in file("java9-rt-export"))
  .settings(
    name := "java9-rt-export",
    autoScalaLibrary := false,
    crossPaths := false,
    description := "Exports the contents of the Java 9. JEP-220 runtime image to a JAR for compatibility with older tools.",
    homepage := Some(url("http://github.com/retronym/" + name.value)),
    startYear := Some(2017),
    licenses += ("Scala license", url(homepage.value.get.toString + "/blob/master/LICENSE")),
    mainClass in Compile := Some("io.github.retronym.java9rtexport.Export")
  )

def downloadUrlForVersion(v: String) = (v split "[^\\d]" flatMap (i => catching(classOf[Exception]) opt (i.toInt))) match {
  case Array(0, 11, 3, _*)           => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.11.3-2/sbt-launch.jar"
  case Array(0, 11, x, _*) if x >= 3 => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case Array(0, y, _*) if y >= 12    => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case Array(1, _, _*) if v contains ("-20") => "http://repo.scala-sbt.org/scalasbt/maven-snapshots/org/scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case Array(1, _, _*)               => "http://repo.scala-sbt.org/scalasbt/maven-releases/org/scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case _                             => "http://repo.typesafe.com/typesafe/ivy-releases/org.scala-tools.sbt/sbt-launch/"+v+"/sbt-launch.jar"
}

def makePublishToForConfig(config: Configuration) = {
  val v = sbtVersionToRelease

  // Add the publish to and ensure global resolvers has the resolver we just configured.
  inConfig(config)(Seq(
    name := "sbt",
    bintrayOrganization := {
      // offline installation exceeds 50MB file limit for OSS organization
      if (sbtOfflineInstall) Some("sbt")
      else Some("sbt")
    },
    bintrayRepository := bintrayTripple.value._1,
    bintrayRepo := Bintray.cachedRepo(bintrayEnsureCredentials.value,
      bintrayOrganization.value,
      bintrayRepository.value),
    bintrayPackage := "sbt",

    bintrayDebianUrl             := s"https://api.bintray.com/content/${bintrayOrganization.value.get}/debian/",
    bintrayDebianExperimentalUrl := s"https://api.bintray.com/content/${bintrayOrganization.value.get}/debian-experimental/",
    bintrayRpmUrl                := s"https://api.bintray.com/content/${bintrayOrganization.value.get}/rpm/",
    bintrayRpmExperimentalUrl    := s"https://api.bintray.com/content/${bintrayOrganization.value.get}/rpm-experimental/",
    bintrayGenericPackagesUrl    := s"https://api.bintray.com/content/${bintrayOrganization.value.get}/native-packages/",
    bintrayTripple := {
      config.name match {
        case Debian.name if isExperimental => ("debian-experimental", bintrayDebianExperimentalUrl.value, bintrayLinuxPattern)
        case Debian.name                   => ("debian", bintrayDebianUrl.value, bintrayLinuxPattern)
        case Rpm.name if isExperimental    => ("rpm-experimental", bintrayRpmExperimentalUrl.value, bintrayLinuxPattern)
        case Rpm.name                      => ("rpm", bintrayRpmUrl.value, bintrayLinuxPattern)
        case _                             => ("native-packages", bintrayGenericPackagesUrl.value, bintrayGenericPattern)
      }
    },
    publishTo := {
      val (id, url, pattern) = bintrayTripple.value
      val resolver = Resolver.url(id, new URL(url))(Patterns(pattern))
      Some(resolver)
    },
    bintrayReleaseAllStaged := bintrayRelease(bintrayRepo.value, bintrayPackage.value, version.value, sLog.value)
    // Uncomment to release right after publishing
    // publish <<= (publish, bintrayRepo, bintrayPackage, version, sLog) apply { (publish, bintrayRepo, bintrayPackage, version, sLog) =>
    //   for {
    //     pub <- publish
    //     repo <- bintrayRepo
    //   } yield bintrayRelease(repo, bintrayPackage, version, sLog)
    // }
  )) ++ Seq(
     resolvers ++= ((publishTo in config) apply (_.toSeq)).value
  )
}

def publishToSettings =
  Seq[Configuration](Debian, Universal, Windows, Rpm) flatMap makePublishToForConfig

def bintrayRelease(repo: BintrayRepo, pkg: String, version: String, log: Logger): Unit =
  repo.release(pkg, version, log)

def downloadUrl(uri: URI, out: File): Unit =
  {
    import dispatch.classic._
    if(!out.exists) {
       IO.touch(out)
       val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(out))
       try Http(url(uri.toString) >>> writer)
       finally writer.close()
    }
  }

lazy val dist = (project in file("dist"))
  .enablePlugins(ExportRepoPlugin)
  .settings(
    name := "dist",
    scalaVersion := {
      if (sbtVersionToRelease startsWith "0.13.") scala210
      else scala212
    },
    libraryDependencies ++= Seq(sbtActual, jansi, scala212Compiler, scala212Jline, scala212Xml) ++ sbt013ExtraDeps,
    exportRepo := {
      val old = exportRepo.value
      sbtVersionToRelease match {
        case v if v.startsWith("1.") =>
          sys.error("sbt 1.x should use coursier")
        case v if v.startsWith("0.13.") =>
          val outbase = exportRepoDirectory.value / "org.scala-sbt" / "compiler-interface" / v
          val uribase = s"https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/compiler-interface/$v/"
          downloadUrl(uri(uribase + "ivys/ivy.xml"), outbase / "ivys" / "ivy.xml")
          downloadUrl(uri(uribase + "jars/compiler-interface.jar"), outbase / "jars" / "compiler-interface.jar")
          downloadUrl(uri(uribase + "srcs/compiler-interface-sources.jar"), outbase / "srcs" / "compiler-interface-sources.jar")
        case _ =>
      }
      old
    },
    exportRepoDirectory := target.value / "lib" / "local-preloaded",
    exportRepoCsrDirectory := exportRepoDirectory.value,
    exportRepoUsingCoursier := {
      val outDirectory = exportRepoCsrDirectory.value
      val csr =
        if (isWindows) (baseDirectory in LocalRootProject).value / "bin" / "coursier.bat"
        else (baseDirectory in LocalRootProject).value / "bin" / "coursier"
      val cache = target.value / "coursier"
      IO.delete(cache)
      val v = sbtVersionToRelease
      s"$csr fetch --cache $cache org.scala-sbt:sbt:$v".!
      val mavenCache = cache / "https" / "repo1.maven.org" / "maven2"
      val compilerBridgeVer = IO.listFiles(mavenCache / "org" / "scala-sbt" / "compiler-bridge_2.12", DirectoryFilter).toList.headOption
      compilerBridgeVer match {
        case Some(bridgeDir) =>
          val bridgeVer = bridgeDir.getName
          s"$csr fetch --cache $cache --sources org.scala-sbt:compiler-bridge_2.10:$bridgeVer".!
          s"$csr fetch --cache $cache --sources org.scala-sbt:compiler-bridge_2.11:$bridgeVer".!
          s"$csr fetch --cache $cache --sources org.scala-sbt:compiler-bridge_2.12:$bridgeVer".!
          s"$csr fetch --cache $cache --sources org.scala-sbt:compiler-bridge_2.13:$bridgeVer".!
        case _ =>
          sys.error("bridge not found")
      }
      IO.copyDirectory(mavenCache, outDirectory, true, true)
      outDirectory
    },
    conflictWarning := ConflictWarning.disable,
    publish := (),
    publishLocal := (),
    resolvers += Resolver.typesafeIvyRepo("releases")
  )
