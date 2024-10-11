import scala.util.control.Exception.catching
import NativePackagerHelper._
import com.typesafe.sbt.packager.SettingsHelper._
import DebianConstants._

lazy val sbtOfflineInstall =
  sys.props.getOrElse("sbt.build.offline", sys.env.getOrElse("sbt.build.offline", "false")) match {
    case "true" | "1"  => true
    case "false" | "0" => false
    case _             => false
  }
lazy val sbtIncludeSbtn =
  sys.props.getOrElse("sbt.build.includesbtn", sys.env.getOrElse("sbt.build.includesbtn", "true")) match {
    case "true" | "1"  => true
    case "false" | "0" => false
    case _             => false
  }
lazy val sbtIncludeSbtLaunch =
  sys.props.getOrElse("sbt.build.includesbtlaunch", sys.env.getOrElse("sbt.build.includesbtlaunch", "true")) match {
    case "true" | "1"  => true
    case "false" | "0" => false
    case _             => false
  }
lazy val sbtVersionToRelease = sys.props.getOrElse("sbt.build.version", sys.env.getOrElse("sbt.build.version", {
        sys.error("-Dsbt.build.version must be set")
      }))

lazy val scala210 = "2.10.7"
lazy val scala212 = "2.12.20"
lazy val scala210Jline = "org.scala-lang" % "jline" % scala210
lazy val jansi = {
  if (sbtVersionToRelease startsWith "1.") "org.fusesource.jansi" % "jansi" % "1.12"
  else "org.fusesource.jansi" % "jansi" % "1.4"
}
lazy val scala212Compiler = "org.scala-lang" % "scala-compiler" % scala212
lazy val scala212Jline = "jline" % "jline" % "2.14.6"
// use the scala-xml version used by the compiler not the latest: https://github.com/scala/scala/blob/v2.12.20/versions.properties
lazy val scala212Xml = "org.scala-lang.modules" % "scala-xml_2.12" % "2.2.0"
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
val sbtnVersion = SettingKey[String]("sbtn-version")
val sbtnJarsMappings = TaskKey[Seq[(File, String)]]("sbtn-jars-mappings", "Resolves sbtn JARs")
val sbtnJarsBaseUrl = SettingKey[String]("sbtn-jars-base-url")

lazy val bintrayDebianUrl = settingKey[String]("API point for Debian packages")
lazy val bintrayDebianExperimentalUrl = settingKey[String]("API point for Debian experimental packages")
lazy val bintrayRpmUrl = settingKey[String]("API point for RPM packages")
lazy val bintrayRpmExperimentalUrl = settingKey[String]("API point for RPM experimental packages")
lazy val bintrayGenericPackagesUrl = settingKey[String]("API point for generic packages")
lazy val bintrayTripple = settingKey[(String, String, String)]("id, url, and pattern")

val artifactoryLinuxPattern = "[module]-[revision].[ext]"
val artifactoryDebianPattern = "[module]-[revision].[ext];deb.distribution=all;deb.component=main;deb.architecture=all"
val bintrayGenericPattern = "[module]/[revision]/[module]/[revision]/[module]-[revision].[ext]"
val bintrayReleaseAllStaged = TaskKey[Unit]("bintray-release-all-staged", "Release all staged artifacts on bintray.")
val windowsBuildId = settingKey[Int]("build id for Windows installer")
val debianBuildId = settingKey[Int]("build id for Debian")

val exportRepoUsingCoursier = taskKey[File]("export Maven style repository")
val exportRepoCsrDirectory = settingKey[File]("")

val universalMacPlatform = "universal-apple-darwin"
val x86LinuxPlatform = "x86_64-pc-linux"
val aarch64LinuxPlatform = "aarch64-pc-linux"
val x86WindowsPlatform = "x86_64-pc-win32"
val universalMacImageName = s"sbtn-$universalMacPlatform"
val x86LinuxImageName = s"sbtn-$x86LinuxPlatform"
val aarch64LinuxImageName = s"sbtn-$aarch64LinuxPlatform"
val x86WindowsImageName = s"sbtn-$x86WindowsPlatform.exe"

organization in ThisBuild := "org.scala-sbt"
version in ThisBuild := "0.1.0"

// This build creates a SBT plugin with handy features *and* bundles the SBT script for distribution.
val root = (project in file(".")).
  enablePlugins(UniversalPlugin, LinuxPlugin, DebianPlugin, RpmPlugin, WindowsPlugin,
    UniversalDeployPlugin, RpmDeployPlugin, WindowsDeployPlugin).
  settings(
    name := "sbt-launcher-packaging",
    packageName := "sbt",
    crossTarget := target.value,
    clean := {
      val _ = (clean in dist).value
      clean.value
    },
    credentials ++= {
      (sys.env.get("BINTRAY_USER"), sys.env.get("BINTRAY_PASS")) match {
        case (Some(u), Some(p)) => Seq(Credentials("Bintray API Realm", "api.bintray.com", u, p))
        case _ => Nil
      }
    },
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
    // update sbt.sh at root
    sbtnVersion := "1.10.0",
    sbtnJarsBaseUrl := "https://github.com/sbt/sbtn-dist/releases/download",
    sbtnJarsMappings := {
      val baseUrl = sbtnJarsBaseUrl.value
      val v = sbtnVersion.value
      val macosUniversalImageTar = s"sbtn-$universalMacPlatform-$v.tar.gz"
      val linuxX86ImageTar = s"sbtn-$x86LinuxPlatform-$v.tar.gz"
      val linuxAarch64ImageTar = s"sbtn-$aarch64LinuxPlatform-$v.tar.gz"
      val windowsImageZip = s"sbtn-$x86WindowsPlatform-$v.zip"
      val t = target.value
      val macosUniversalTar = t / macosUniversalImageTar
      val linuxX86Tar = t / linuxX86ImageTar
      val linuxAarch64Tar = t / linuxAarch64ImageTar
      val windowsZip = t / windowsImageZip
      import dispatch.classic._
      if(!macosUniversalTar.exists && !isWindows && sbtIncludeSbtn) {
         IO.touch(macosUniversalTar)
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(macosUniversalTar))
         try Http(url(s"$baseUrl/v$v/$macosUniversalImageTar") >>> writer)
         finally writer.close()
         val platformDir = t / universalMacPlatform
         IO.createDirectory(platformDir)
         s"tar zxvf $macosUniversalTar --directory $platformDir".!
         IO.move(platformDir / "sbtn", t / universalMacImageName)
      }
      if(!linuxX86Tar.exists && !isWindows && sbtIncludeSbtn) {
         IO.touch(linuxX86Tar)
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(linuxX86Tar))
         try Http(url(s"$baseUrl/v$v/$linuxX86ImageTar") >>> writer)
         finally writer.close()
         val platformDir = t / x86LinuxPlatform
         IO.createDirectory(platformDir)
         s"""tar zxvf $linuxX86Tar --directory $platformDir""".!
         IO.move(platformDir / "sbtn", t / x86LinuxImageName)
      }
      if(!linuxAarch64Tar.exists && !isWindows && sbtIncludeSbtn) {
         IO.touch(linuxAarch64Tar)
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(linuxAarch64Tar))
         try Http(url(s"$baseUrl/v$v/$linuxAarch64ImageTar") >>> writer)
         finally writer.close()
         val platformDir = t / aarch64LinuxPlatform
         IO.createDirectory(platformDir)
         s"""tar zxvf $linuxAarch64Tar --directory $platformDir""".!
         IO.move(platformDir / "sbtn", t / aarch64LinuxImageName)
      }
      if(!windowsZip.exists && sbtIncludeSbtn) {
         IO.touch(windowsZip)
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(windowsZip))
         try Http(url(s"$baseUrl/v$v/$windowsImageZip") >>> writer)
         finally writer.close()
         val platformDir = t / x86WindowsPlatform
         IO.unzip(windowsZip, platformDir)
         IO.move(platformDir / "sbtn.exe", t / x86WindowsImageName)
      }
      if (!sbtIncludeSbtn) Seq()
      else if (isWindows) Seq(t / x86WindowsImageName -> s"bin/$x86WindowsImageName")
      else
        Seq(t / universalMacImageName -> s"bin/$universalMacImageName",
          t / x86LinuxImageName -> s"bin/$x86LinuxImageName",
          t / aarch64LinuxImageName -> s"bin/$aarch64LinuxImageName",
          t / x86WindowsImageName -> s"bin/$x86WindowsImageName")
    },

    // GENERAL LINUX PACKAGING STUFFS
    maintainer := "Eugene Yokota <eed3si9n@gmail.com>",
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
    debianBuildId := sys.props.getOrElse("sbt.build.patch", sys.env.getOrElse("DIST_PATCHVER", "0")).toInt,
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

    // use the following instead of DebianDeployPlugin to skip changelog
    makeDeploymentSettings(Debian, packageBin in Debian, "deb"),

    // RPM SPECIFIC
    rpmRelease := debianBuildId.value.toString,
    version in Rpm := {
      val stable0 = (sbtVersionToRelease split "[^\\d]" filterNot (_.isEmpty) mkString ".")
      val stable = if (rpmRelease.value == "0") stable0
                   else stable0 + "." + rpmRelease.value
      if (isExperimental) ((sbtVersionToRelease split "[^\\d]" filterNot (_.isEmpty)).toList match {
        case List(a, b, c, d) => List(0, 99, c, d).mkString(".")
      })
      else stable
    },
    // remove sbtn from RPM because it complains about it being noarch
    linuxPackageMappings in Rpm := {
      val orig = (linuxPackageMappings in Rpm).value
      val nativeMappings = sbtnJarsMappings.value
      orig.map(o => o.copy(mappings = o.mappings.toList filterNot {
        case (x, p) => p.contains("sbtn-x86_64") || p.contains("sbtn-aarch64")
      }))
    },
    rpmVendor := "scalacenter",
    rpmUrl := Some("http://github.com/sbt/sbt-launcher-package"),
    rpmLicense := Some("Apache-2.0"),
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
    maintainer in Windows := "Scala Center",
    packageSummary in Windows := "sbt " + (version in Windows).value,
    packageDescription in Windows := "The interactive build tool.",
    wixProductId := "ce07be71-510d-414a-92d4-dff47631848a",
    wixProductUpgradeId := Hash.toHex(Hash((version in Windows).value)).take(32),
    javacOptions := Seq("-source", "1.8", "-target", "1.8"),

    // Universal ZIP download install.
    packageName in Universal := packageName.value, // needs to be set explicitly due to a bug in native-packager
    name in Windows := packageName.value,
    packageName in Windows := packageName.value,
    version in Universal := sbtVersionToRelease,

    mappings in Universal += {
      (baseDirectory.value.getParentFile / "sbt") -> ("bin" + java.io.File.separator + "sbt")
    },

    mappings in Universal := {
      val t = (target in Universal).value
      val prev = (mappings in Universal).value
      val BinSbt = "bin" + java.io.File.separator + "sbt"
      val BinBat = BinSbt + ".bat"
      prev.toList map {
        case (k, BinSbt) =>
          import java.nio.file.{Files, FileSystems}
          val x = IO.read(k)
          IO.write(t / "sbt", x.replace(
            "declare init_sbt_version=_to_be_replaced",
            s"declare init_sbt_version=$sbtVersionToRelease"))

          if (FileSystems.getDefault.supportedFileAttributeViews.contains("posix")) {
            val perms = Files.getPosixFilePermissions(k.toPath)
            Files.setPosixFilePermissions(t / "sbt" toPath, perms)
          }

          (t / "sbt", BinSbt)
        case (k, BinBat) =>
          val x = IO.read(k)
          IO.write(t / "sbt.bat", x.replaceAllLiterally(
            "set init_sbt_version=_to_be_replaced",
            s"set init_sbt_version=$sbtVersionToRelease"))
          (t / "sbt.bat", BinBat)
        case (k, v) => (k, v)
      }
    },
    mappings in Universal ++= (Def.taskDyn {
      if (sbtIncludeSbtLaunch)
        Def.task {
          Seq(
            sbtLaunchJar.value -> "bin/sbt-launch.jar"
          )
        }
      else Def.task { Seq[(File, String)]() }
    }).value,
    mappings in Universal ++= sbtnJarsMappings.value,
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
      else Seq[(File, String)](base.getParentFile / "LICENSE" -> "LICENSE", base / "NOTICE" -> "NOTICE")
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
    scalaVersion := scala212,
    libraryDependencies ++= Seq(
      "io.monix" %% "minitest" % "2.3.2" % Test,
      "com.eed3si9n.expecty" %% "expecty" % "0.11.0" % Test,
      "org.scala-sbt" %% "io" % "1.3.1" % Test
    ),
    testFrameworks += new TestFramework("minitest.runner.Framework"),
    test in Test := {
      (test in Test).dependsOn(((packageBin in Universal) in LocalRootProject).dependsOn(((stage in (Universal) in LocalRootProject)))).value
    },
    testOnly in Test := {
      (testOnly in Test).dependsOn(((packageBin in Universal) in LocalRootProject).dependsOn(((stage in (Universal) in LocalRootProject)))).evaluated
    }
  )

def downloadUrlForVersion(v: String) = (v split "[^\\d]" flatMap (i => catching(classOf[Exception]) opt (i.toInt))) match {
  case Array(0, 11, 3, _*)           => "https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.11.3-2/sbt-launch.jar"
  case Array(0, 11, x, _*) if x >= 3 => "https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case Array(0, y, _*) if y >= 12    => "https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case Array(1, _, _*) if v contains ("-20") => "https://repo.scala-sbt.org/scalasbt/maven-snapshots/org/scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case _                             => "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/"+v+"/sbt-launch-"+v+".jar"
}

def makePublishToForConfig(config: Configuration) = {
  val v = sbtVersionToRelease

  // Add the publish to and ensure global resolvers has the resolver we just configured.
  inConfig(config)(Seq(
    name := "sbt",
    bintrayDebianUrl             := s"https://scala.jfrog.io/artifactory/debian/",
    bintrayDebianExperimentalUrl := s"https://scala.jfrog.io/artifactory/debian-experimental/",
    bintrayRpmUrl                := s"https://scala.jfrog.io/artifactory/rpm/",
    bintrayRpmExperimentalUrl    := s"https://scala.jfrog.io/artifactory/rpm-experimental/",
    bintrayGenericPackagesUrl    := s"https://scala.jfrog.io/artifactory/native-packages/",
    bintrayTripple := {
      config.name match {
        case Debian.name if isExperimental => ("debian-experimental", bintrayDebianExperimentalUrl.value, artifactoryDebianPattern)
        case Debian.name                   => ("debian", bintrayDebianUrl.value, artifactoryDebianPattern)
        case Rpm.name if isExperimental    => ("rpm-experimental", bintrayRpmExperimentalUrl.value, artifactoryLinuxPattern)
        case Rpm.name                      => ("rpm", bintrayRpmUrl.value, artifactoryLinuxPattern)
      }
    },
    publishTo := {
      val (id, url, pattern) = bintrayTripple.value
      val resolver = Resolver.url(id, new URL(url))(Patterns(pattern))
      Some(resolver)
    }
  )) ++ Seq(
     resolvers ++= ((publishTo in config) apply (_.toSeq)).value
  )
}

def publishToSettings =
  Seq[Configuration](Debian, Rpm) flatMap makePublishToForConfig

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

def colonName(m: ModuleID): String = s"${m.organization}:${m.name}:${m.revision}"

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
      s"$csr fetch --cache $cache ${colonName(jansi)}".!
      s"$csr fetch --cache $cache ${colonName(scala212Compiler)}".!
      s"$csr fetch --cache $cache ${colonName(scala212Xml)}".!
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
