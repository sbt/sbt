import sbt._
import Keys._

object DebianPkg {
  val Debian = config("debian-pkg")
  val makeDebianExplodedPackage = TaskKey[File]("make-debian-exploded-package")
  val makeZippedPackageSource = TaskKey[Unit]("make-zipped-package-source")
  val genControlFile = TaskKey[File]("generate-control-file")
  val lintian = TaskKey[Unit]("lintian", "Displays lintian error messages associated with the package")
  val showMan = TaskKey[Unit]("show-man", "shows the sbt program man page")

  val settings: Seq[Setting[_]] = Seq(
    resourceDirectory in Debian <<= baseDirectory(_ / "src" / "debian"),
    resourceDirectory in Debian in makeZippedPackageSource <<= baseDirectory(_ / "src" / "debian-gzipped"),
    mappings in Debian <<= resourceDirectory in Debian map { d => (d.*** --- d) x (relativeTo(d)) },
    mappings in Debian in makeZippedPackageSource <<= resourceDirectory in Debian in makeZippedPackageSource map { d => (d.*** --- d) x (relativeTo(d)) },
    // TODO - put sbt-version into the generated sbt script.
    mappings in Debian <+= baseDirectory map { dir => (dir / "sbt" -> "usr/bin/sbt") },
    name in Debian := "sbt",
    version in Debian <<= (version, sbtVersion) apply { (v, sv) =>       
       sv + "-build-" + (v split "\\." map (_.toInt) dropWhile (_ == 0) map ("%02d" format _) mkString "")
    },
    target in Debian <<= (target, name in Debian, version in Debian) apply ((t,n, v) => t / (n +"-"+ v)),
    genControlFile <<= (name in Debian, version in Debian, target in Debian) map {
      (name, version, dir) =>
        val cfile = dir / "DEBIAN" / "control"
        IO.writer(cfile, ControlFileContent, java.nio.charset.Charset.defaultCharset, false) { o =>
           val out = new java.io.PrintWriter(o)
           out println ("Package: %s" format name)
           out println ("Version: %s" format version)
           out println ControlFileContent
        }
        cfile
    },
    makeDebianExplodedPackage <<= (mappings in Debian, genControlFile, target in Debian) map { (files, _, dir) =>
       for((file, target) <- files) {
          val tfile = dir / target
          if(file.isDirectory) IO.createDirectory(tfile)
          else IO.copyFile(file,tfile)
        }
        dir      
    },
    makeZippedPackageSource <<= (mappings in Debian in makeZippedPackageSource, target in Debian) map { (files, dir) =>
        for((file, target) <- files) {
          val tfile = dir / target
          if(file.isDirectory) IO.createDirectory(tfile)
          else {
            val zipped = new File(tfile.getAbsolutePath + ".gz")
            IO delete zipped
            IO.copyFile(file,tfile)
            Process(Seq("gzip", "--best", tfile.getAbsolutePath), Some(tfile.getParentFile)).!
          }
        }
        dir      
    },
    packageBin in Debian <<= (makeDebianExplodedPackage, makeZippedPackageSource, target in Debian, name in Debian, version in Debian) map { (pkgdir, _, tdir, n, v) =>
       // Assign appropriate permissions
       val isDirectory = (_: File).isDirectory
       val dirs = (tdir.***).get filter isDirectory
       val bins = (tdir / "usr" / "bin" ***).get filterNot isDirectory
       val data = (tdir / "usr" / "share" ***).get filterNot isDirectory       
       val perms = Map("0755" -> (dirs ++ bins), 
                       "0644" -> data)
       val commands = for {
         (perm, files) <- perms
         file <- files
         p = Process("chmod " + perm + " " + file.getAbsolutePath)
       } p.!

       // Make the package.  We put this in fakeroot, so we can build the package with root owning files.
       Process(Seq("fakeroot", "--", "dpkg-deb", "--build", pkgdir.getAbsolutePath), Some(tdir)).!
      tdir.getParentFile / (n + "-" + v + ".deb")
    },
    lintian <<= (packageBin in Debian) map { file =>
       println("lintian -c " + file.getName + " on " + file.getParentFile.getAbsolutePath)
       Process(Seq("lintian", "-c", "-v", file.getName), Some(file.getParentFile)).!
    },
    showMan <<= (resourceDirectory in Debian in makeZippedPackageSource) map { dir =>
      Process("groff -man -Tascii " + (dir / "usr" / "share" / "man" / "man1" / "sbt.1").getAbsolutePath).!
    }
  )

  // TODO - Use default-jre-headless?
  final val ControlFileContent = """Section: java
Priority: optional
Architecture: all
Depends:   curl, java2-runtime, bash (>= 2.05a-11)
Recommends: git
Maintainer: Josh Suereth <joshua.suereth@typesafe.com>
Description: Simple Build Tool
 This script provides a native way to run the Simple Build Tool,
 a build tool for Scala software, also called SBT.
"""
}
