import scala.util.control.Exception.catching
import scala.sys.process.*
import NativePackagerHelper.*
import com.typesafe.sbt.packager.SettingsHelper.*
import DebianConstants.*
import Dependencies.*

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
lazy val sbtVersionToRelease = sys.props
  .getOrElse("sbt.build.version", sys.env.getOrElse("sbt.build.version", "1.12.0"))

lazy val scala210 = "2.10.7"
lazy val scala210Jline = "org.scala-lang" % "jline" % scala210
lazy val jansi = {
  if (sbtVersionToRelease startsWith "1.") "org.fusesource.jansi" % "jansi" % "1.12"
  else "org.fusesource.jansi" % "jansi" % "1.4"
}
lazy val scala212Compiler = "org.scala-lang" % "scala-compiler" % scala212
lazy val scala212Jline = "jline" % "jline" % "2.14.6"
// use the scala-xml version used by the compiler not the latest: https://github.com/scala/scala/blob/v2.12.21/versions.properties
lazy val scala212Xml = "org.scala-lang.modules" % "scala-xml_2.12" % "2.3.0"
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
val exportRepo = taskKey[File]("export Ivy style repository")
val exportRepoDirectory = settingKey[File]("directory for exported repository")

val universalMacPlatform = "universal-apple-darwin"
val x86LinuxPlatform = "x86_64-pc-linux"
val aarch64LinuxPlatform = "aarch64-pc-linux"
val x86WindowsPlatform = "x86_64-pc-win32"
val universalMacImageName = s"sbtn-$universalMacPlatform"
val x86LinuxImageName = s"sbtn-$x86LinuxPlatform"
val aarch64LinuxImageName = s"sbtn-$aarch64LinuxPlatform"
val x86WindowsImageName = s"sbtn-$x86WindowsPlatform.exe"

Global / excludeLintKeys += bintrayGenericPackagesUrl

// This build creates a SBT plugin with handy features *and* bundles the SBT script for distribution.
val launcherPackage = (project in file(".")).
  enablePlugins(UniversalPlugin, LinuxPlugin, DebianPlugin, RpmPlugin, WindowsPlugin,
    UniversalDeployPlugin, RpmDeployPlugin, WindowsDeployPlugin).
  settings(
    name := "sbt-launcher-packaging",
    packageName := "sbt",
    crossTarget := target.value,
    clean := {
      val _ = (dist / clean).value
      clean.value
    },
    credentials ++= {
      (sys.env.get("BINTRAY_USER"), sys.env.get("BINTRAY_PASS")) match {
        case (Some(u), Some(p)) => Seq(Credentials("Bintray API Realm", "api.bintray.com", u, p))
        case _ => Nil
      }
    },
    pgpSecretRing := file(s"""${sys.props("user.home")}""") / ".ssh" / "scalasbt.key",
    pgpPublicRing := file(s"""${sys.props("user.home")}""") / ".ssh" / "scalasbt.pub",
    publishToSettings,
    sbtLaunchJarUrl := downloadUrlForVersion(sbtVersionToRelease),
    sbtLaunchJarLocation := { target.value / "sbt-launch.jar" },
    sbtLaunchJar := {
      val uri = sbtLaunchJarUrl.value
      val file = sbtLaunchJarLocation.value
      if(!file.exists) {
         // oddly, some places require us to create the file before writing...
         IO.touch(file)
         val url = new URI(uri).toURL
         val connection = url.openConnection()
         val input = connection.getInputStream
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(file))
         try {
           val buffer = new Array[Byte](8192)
           var bytesRead = input.read(buffer)
           while (bytesRead != -1) {
             writer.write(buffer, 0, bytesRead)
             bytesRead = input.read(buffer)
           }
         } finally {
           input.close()
           writer.close()
         }
      }
      // TODO - GPG Trust validation.
      file
    },
    // update sbt.sh at root
    sbtnVersion := "1.12.1",
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
      if(!macosUniversalTar.exists && !isWindows && sbtIncludeSbtn) {
         IO.touch(macosUniversalTar)
         val url = new URI(s"$baseUrl/v$v/$macosUniversalImageTar").toURL
         val connection = url.openConnection()
         val input = connection.getInputStream
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(macosUniversalTar))
         try {
           val buffer = new Array[Byte](8192)
           var bytesRead = input.read(buffer)
           while (bytesRead != -1) {
             writer.write(buffer, 0, bytesRead)
             bytesRead = input.read(buffer)
           }
         } finally {
           input.close()
           writer.close()
         }
         val platformDir = t / universalMacPlatform
         IO.createDirectory(platformDir)
         s"tar zxvf $macosUniversalTar --directory $platformDir".!
         IO.move(platformDir / "sbtn", t / universalMacImageName)
      }
      if(!linuxX86Tar.exists && !isWindows && sbtIncludeSbtn) {
         IO.touch(linuxX86Tar)
         val url = new URI(s"$baseUrl/v$v/$linuxX86ImageTar").toURL
         val connection = url.openConnection()
         val input = connection.getInputStream
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(linuxX86Tar))
         try {
           val buffer = new Array[Byte](8192)
           var bytesRead = input.read(buffer)
           while (bytesRead != -1) {
             writer.write(buffer, 0, bytesRead)
             bytesRead = input.read(buffer)
           }
         } finally {
           input.close()
           writer.close()
         }
         val platformDir = t / x86LinuxPlatform
         IO.createDirectory(platformDir)
         s"""tar zxvf $linuxX86Tar --directory $platformDir""".!
         IO.move(platformDir / "sbtn", t / x86LinuxImageName)
      }
      if(!linuxAarch64Tar.exists && !isWindows && sbtIncludeSbtn) {
         IO.touch(linuxAarch64Tar)
         val url = new URI(s"$baseUrl/v$v/$linuxAarch64ImageTar").toURL
         val connection = url.openConnection()
         val input = connection.getInputStream
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(linuxAarch64Tar))
         try {
           val buffer = new Array[Byte](8192)
           var bytesRead = input.read(buffer)
           while (bytesRead != -1) {
             writer.write(buffer, 0, bytesRead)
             bytesRead = input.read(buffer)
           }
         } finally {
           input.close()
           writer.close()
         }
         val platformDir = t / aarch64LinuxPlatform
         IO.createDirectory(platformDir)
         s"""tar zxvf $linuxAarch64Tar --directory $platformDir""".!
         IO.move(platformDir / "sbtn", t / aarch64LinuxImageName)
      }
      if(!windowsZip.exists && sbtIncludeSbtn) {
         IO.touch(windowsZip)
         val url = new URI(s"$baseUrl/v$v/$windowsImageZip").toURL
         val connection = url.openConnection()
         val input = connection.getInputStream
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(windowsZip))
         try {
           val buffer = new Array[Byte](8192)
           var bytesRead = input.read(buffer)
           while (bytesRead != -1) {
             writer.write(buffer, 0, bytesRead)
             bytesRead = input.read(buffer)
           }
         } finally {
           input.close()
           writer.close()
         }
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
    Debian / version := {
      if (debianBuildId.value == 0) sbtVersionToRelease
      else sbtVersionToRelease + "." + debianBuildId.value
    },
    // Used to have "openjdk-8-jdk" but that doesn't work on Ubuntu 14.04 https://github.com/sbt/sbt/issues/3105
    // before that we had java6-runtime-headless" and that was pulling in JDK9 on Ubuntu 16.04 https://github.com/sbt/sbt/issues/2931
    Debian / debianPackageDependencies ++= Seq("bash (>= 3.2)", "curl | wget"),
    Debian / debianPackageRecommends += "git",
    Debian / linuxPackageMappings += {
      val bd = sourceDirectory.value
      (packageMapping(
        (bd / "debian" / "changelog") -> "/usr/share/doc/sbt/changelog.gz"
      ) withUser "root" withGroup "root" withPerms "0644" gzipped) asDocs()
    },
    Debian / debianChangelog := { Some(sourceDirectory.value / "debian" / "changelog") },
    addPackage(Debian, (Debian / packageBin), "deb"),
    Debian / debianNativeBuildOptions := Seq("-Zgzip", "-z3"),

    // use the following instead of DebianDeployPlugin to skip changelog
    makeDeploymentSettings(Debian, (Debian / packageBin), "deb"),

    // RPM SPECIFIC
    rpmRelease := debianBuildId.value.toString,
    Rpm / version := {
      val stable0 = (sbtVersionToRelease split "[^\\d]" filterNot (_.isEmpty) mkString ".")
      val stable = if (rpmRelease.value == "0") stable0
                   else stable0 + "." + rpmRelease.value
      if (isExperimental) ((sbtVersionToRelease split "[^\\d]" filterNot (_.isEmpty)).toList match {
        case List(_, _, c, d) => List(0, 99, c, d).mkString(".")
      })
      else stable
    },
    // remove sbtn from RPM because it complains about it being noarch
    Rpm / linuxPackageMappings := {
      val orig = ((Rpm / linuxPackageMappings)).value
      val _ = sbtnJarsMappings.value
      orig.map(o => o.copy(mappings = o.mappings.toList filterNot {
        case (_, p) => p.contains("sbtn-x86_64") || p.contains("sbtn-aarch64")
      }))
    },
    rpmVendor := "scalacenter",
    rpmUrl := Some("https://github.com/sbt/sbt"),
    rpmLicense := Some("Apache-2.0"),
    // This is intentionally does not list Java. java-devel could bring in JDK 9-ea on Fedora,
    // and java-1.8.0-devel doesn't work on CentOS 6 and 7.
    // https://github.com/sbt/sbt-launcher-package/issues/151
    // https://github.com/elastic/logstash/issues/6275#issuecomment-261359933
    rpmRequirements := Seq("coreutils"),
    rpmProvides := Seq("sbt"),

    // WINDOWS SPECIFIC
    windowsBuildId := 0,
    Windows / version := {
      val bid = windowsBuildId.value
      val sv = sbtVersionToRelease
      (sv split "[^\\d]" filterNot (_.isEmpty)) match {
        case Array(major,minor,bugfix, _*) if bid == 0 => Seq(major, minor, bugfix) mkString "."
        case Array(major,minor,bugfix, _*) => Seq(major, minor, bugfix, bid.toString) mkString "."
        case Array(major,minor) => Seq(major, minor, "0", bid.toString) mkString "."
        case Array(major) => Seq(major, "0", "0", bid.toString) mkString "."
      }
    },
    Windows / maintainer := "Scala Center",
    Windows / packageSummary := "sbt " + (Windows / version).value,
    Windows / packageDescription := "The interactive build tool.",
    wixProductId := "ce07be71-510d-414a-92d4-dff47631848a",
    wixProductUpgradeId := Hash.toHex(Hash((Windows / version).value)).take(32),
    javacOptions := Seq("-source", "1.8", "-target", "1.8"),

    // Universal ZIP download install.
    Universal / packageName := packageName.value, // needs to be set explicitly due to a bug in native-packager
    Windows / name := packageName.value,
    Windows / packageName := packageName.value,
    Universal / version := sbtVersionToRelease,

    Universal / mappings += {
      (baseDirectory.value.getParentFile / "sbt") -> ("bin" + java.io.File.separator + "sbt")
    },

    Universal / mappings := {
      val t = (Universal / target).value
      val prev = (Universal / mappings).value
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
    Universal / mappings ++= (Def.taskDyn {
      if (sbtIncludeSbtLaunch)
        Def.task {
          Seq(
            sbtLaunchJar.value -> "bin/sbt-launch.jar"
          )
        }
      else Def.task { Seq[(File, String)]() }
    }).value,
    Universal / mappings ++= sbtnJarsMappings.value,
    Universal / mappings ++= (Def.taskDyn {
      if (sbtOfflineInstall && sbtVersionToRelease.startsWith("1."))
        Def.task {
          val _ = ((dist / exportRepoUsingCoursier)).value
          directory(((dist / target)).value / "lib")
        }
      else if (sbtOfflineInstall)
        Def.task {
          val _ = ((dist / exportRepo)).value
          directory(((dist / target)).value / "lib")
        }
      else Def.task { Seq[(File, String)]() }
    }).value,
    Universal / mappings ++= {
      val base = baseDirectory.value
      if (sbtVersionToRelease startsWith "0.13.") Nil
      else Seq[(File, String)](base.getParentFile / "LICENSE" -> "LICENSE", base / "NOTICE" -> "NOTICE")
    },

    // Miscellaneous publishing stuff...
    Debian / projectID := {
      val m = moduleID.value
      m.withRevision((Debian / version).value)
    },
    Windows / projectID := {
      val m = moduleID.value
      m.withRevision((Windows / version).value)
    },
    Rpm / projectID := {
      val m = moduleID.value
      m.withRevision((Rpm / version).value)
    },
    Universal / projectID := {
      val m = moduleID.value
      m.withRevision((Universal / version).value)
    }
  )

def downloadUrlForVersion(v: String) = (v.split("[^\\d]") flatMap (i => catching(classOf[Exception]) opt (i.toInt))) match {
  case Array(0, 11, 3, _*)           => "https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.11.3-2/sbt-launch.jar"
  case Array(0, 11, x, _*) if x >= 3 => "https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case Array(0, y, _*) if y >= 12    => "https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case Array(1, _, _*) if v contains ("-20") => "https://repo.scala-sbt.org/scalasbt/maven-snapshots/org/scala-sbt/sbt-launch/"+v+"/sbt-launch.jar"
  case _                             => "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/"+v+"/sbt-launch-"+v+".jar"
}

def makePublishToForConfig(config: Configuration) = {
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
      val resolver = Resolver.url(id, new URI(url).toURL)(Patterns(pattern))
      Some(resolver)
    }
  ))
}

def publishToSettings =
  Seq[Configuration](Debian, Rpm) flatMap makePublishToForConfig

def downloadUrl(uri: URI, out: File): Unit =
  {
    if(!out.exists) {
       IO.touch(out)
       val url = new URI(uri.toString).toURL
       val connection = url.openConnection()
       val input = connection.getInputStream
       val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(out))
       try {
         val buffer = new Array[Byte](8192)
         var bytesRead = input.read(buffer)
         while (bytesRead != -1) {
           writer.write(buffer, 0, bytesRead)
           bytesRead = input.read(buffer)
         }
       } finally {
         input.close()
         writer.close()
       }
    }
  }

def colonName(m: ModuleID): String = s"${m.organization}:${m.name}:${m.revision}"

lazy val dist = (project in file("dist"))
  .settings(
    name := "dist",
    scalaVersion := {
      if (sbtVersionToRelease startsWith "0.13.") scala210
      else scala212
    },
    libraryDependencies ++= Seq(sbtActual, jansi, scala212Compiler, scala212Jline, scala212Xml) ++ sbt013ExtraDeps,
    exportRepo := {
      val outDir = exportRepoDirectory.value
      sbtVersionToRelease match {
        case v if v.startsWith("1.") =>
          sys.error("sbt 1.x should use coursier")
        case v if v.startsWith("0.13.") =>
          val outbase = outDir / "org.scala-sbt" / "compiler-interface" / v
          val uribase = s"https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/compiler-interface/$v/"
          downloadUrl(uri(uribase + "ivys/ivy.xml"), outbase / "ivys" / "ivy.xml")
          downloadUrl(uri(uribase + "jars/compiler-interface.jar"), outbase / "jars" / "compiler-interface.jar")
          downloadUrl(uri(uribase + "srcs/compiler-interface-sources.jar"), outbase / "srcs" / "compiler-interface-sources.jar")
        case _ =>
      }
      outDir
    },
    exportRepoDirectory := target.value / "lib" / "local-preloaded",
    exportRepoCsrDirectory := exportRepoDirectory.value,
    exportRepoUsingCoursier := {
      val outDirectory = exportRepoCsrDirectory.value
      val csr =
        if (isWindows) (LocalRootProject / baseDirectory).value / "bin" / "coursier.bat"
        else (LocalRootProject / baseDirectory).value / "bin" / "coursier"
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
    publish := {},
    publishLocal := {},
    resolvers += Resolver.typesafeIvyRepo("releases")
  )
