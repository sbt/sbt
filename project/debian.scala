import sbt._
import com.typesafe.packager.debian.DebianPlugin._
import com.typesafe.packager.debian.Keys._
import com.typesafe.packager.linux.{LinuxPackageMapping, LinuxFileMetaData}
import com.typesafe.packager.rpm.RpmPlugin._
import com.typesafe.packager.rpm.Keys._
import sbt.Keys.{baseDirectory,sbtVersion,sourceDirectory, name,version}
import com.typesafe.packager.linux.Keys.{linuxPackageMappings,maintainer,packageDescription}

object DebianPkg {
  
  val settings: Seq[Setting[_]] = debianSettings ++ rpmSettings ++ Seq(
      
    // GENERAL LINUX PACKAGING STUFFS
    maintainer := "Josh Suereth <joshua.suereth@typesafe.com>",
    packageDescription := """Simple Build Tool
 This script provides a native way to run the Simple Build Tool,
 a build tool for Scala software, also called SBT.""",
    linuxPackageMappings <+= (baseDirectory) map { bd =>
      (packageForDebian((bd / "sbt") -> "/usr/bin/sbt")
       withUser "root" withGroup "root" withPerms "0755")
    },
    linuxPackageMappings <+= (sourceDirectory) map { bd =>
      (packageForDebian(
        (bd / "linux" / "usr/share/man/man1/sbt.1") -> "/usr/share/man/man1/sbt.1.gz"
      ) withUser "root" withGroup "root" withPerms "0644" gzipped) asDocs()
    },
    linuxPackageMappings <+= (sourceDirectory in Debian) map { bd =>
    packageForDebian(
        (bd / "usr/share/doc/sbt") -> "/usr/share/doc/sbt",
        (bd / "usr/share/doc/sbt/copyright") -> "/usr/share/doc/sbt/copyright"
        ) withUser "root" withGroup "root" withPerms "0644" asDocs()
    },   

    // DEBIAN SPECIFIC    
    name in Debian := "sbt",
    version in Debian <<= (version, sbtVersion) apply { (v, sv) =>       
      sv + "-build-" + (v split "\\." map (_.toInt) dropWhile (_ == 0) map ("%02d" format _) mkString "")
    },
    debianPackageDependencies in Debian ++= Seq("curl", "java2-runtime", "bash (>= 2.05a-11)"),
    debianPackageRecommends in Debian += "git",
    linuxPackageMappings in Debian <+= (sourceDirectory) map { bd =>
    (packageForDebian(
        (bd / "debian/changelog") -> "/usr/share/doc/sbt/changelog.gz"
        ) withUser "root" withGroup "root" withPerms "0644" gzipped) asDocs()
    },
    
    // RPM SPECIFIC
    name in Rpm := "sbt",
    version in Rpm <<= sbtVersion.identity,
    rpmRelease := "1",
    rpmVendor := "Typesafe, Inc.",
    rpmOs := "i386",
    rpmUrl := Some("http://github.com/paulp/sbt-extras")
  )
}
