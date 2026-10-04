import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*

ThisBuild / scalaVersion := "3.9.0"
ThisBuild / usePipelining := true

Global / localCacheDirectory := baseDirectory.value / "diskcache"

// Relative names with forward slashes, as the zip entries have them.
def relativeNames(dir: File): List[(File, String)] =
  sbt.io.Path.allSubpaths(dir).toList.map((f, n) => (f, n.replace(File.separatorChar, '/')))

def classNames(names: Iterable[String]): Set[String] =
  names.filter(_.endsWith(".class")).map(_.split('/').last.takeWhile(_ != '$').stripSuffix(".class")).toSet

// A blob's name in the disk cache is its content hash and size; a product written through a
// restored link lands there under the old name with new content.
def blobName(p: java.nio.file.Path): String =
  val digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))
  s"sha256-${digest.map("%02x".format(_)).mkString}-${Files.size(p)}"

// The class directory and the jar must hold the products of the current sources, no more; no
// link may dangle; no product may have been written through a restored link into the disk
// cache; the early output must carry every pickle.
def checkProducts(
    expected: Set[String],
    classesDir: File,
    earlyOutput: Option[File],
    jar: File,
): Unit =
  val products = relativeNames(classesDir)
  val onDisk = classNames(products.map(_._2))
  assert(onDisk == expected, s"class directory holds $onDisk, expected $expected")
  val dangling = products.collect { case (f, name) if !f.exists => name }
  assert(dangling.isEmpty, s"dangling links: $dangling")
  val corrupted = products.collect {
    case (f, name) if Files.isSymbolicLink(f.toPath) =>
      val blob = f.toPath.toRealPath()
      Option.when(blob.getFileName.toString != blobName(blob))(s"$name -> ${blob.getFileName}")
  }.flatten
  assert(corrupted.isEmpty, s"products written through restored links: $corrupted")
  earlyOutput.foreach { early =>
    val zip = new ZipFile(early)
    val inEarly =
      try zip.entries.asScala.map(_.getName).filter(_.endsWith(".tasty")).toSet
      finally zip.close()
    val onDiskTasty = products.map(_._2).filter(_.endsWith(".tasty")).toSet
    assert(inEarly == onDiskTasty, s"early output holds $inEarly, class directory $onDiskTasty")
  }
  val zip = new ZipFile(jar)
  val inJar =
    try classNames(zip.entries.asScala.map(_.getName).toList)
    finally zip.close()
  assert(inJar == expected, s"$jar holds $inJar, expected $expected")

// Deletes the cache blobs behind restored links, leaving the links dangling, as a shared cache
// does once another checkout has recreated most of the blobs. Where restores are copies
// (Windows without the symlink privilege, APFS) there is nothing to break and the scenario
// degrades to an ordinary compile.
def breakLinks(paths: Seq[java.nio.file.Path], log: Logger): Unit =
  paths.filter(Files.isSymbolicLink) match
    case Nil   => log.info(s"restores are copies here, nothing to break")
    case links => links.foreach(p => Files.deleteIfExists(Files.readSymbolicLink(p)))

lazy val core = project
  .settings(
    TaskKey[Unit]("checkProducts") := Def.uncached {
      val c = fileConverter.value
      checkProducts(
        (Compile / sources).value.map(_.getName.stripSuffix(".scala")).toSet,
        (Compile / classDirectory).value,
        Option.when(usePipelining.value)(c.toPath((Compile / earlyOutput).value).toFile),
        c.toPath((Compile / packageBin).value).toFile,
      )
    },
    TaskKey[Unit]("breakAnalysis") := Def.uncached {
      breakLinks(Seq((Compile / compileAnalysisFile).value.toPath), streams.value.log)
    },
    TaskKey[Unit]("breakEarlyOutput") := Def.uncached {
      breakLinks(Seq(fileConverter.value.toPath((Compile / earlyOutput).value)), streams.value.log)
    },
    // the products of Other.scala, which the scenarios using this task do not change
    TaskKey[Unit]("breakClassLinks") := Def.uncached {
      val products = relativeNames((Compile / classDirectory).value)
      val other = products.collect { case (f, n) if n.endsWith("/Other.class") || n.endsWith("/Other$.class") => f.toPath }
      assert(other.size == 2, s"expected the two class files of Other, found $other")
      breakLinks(other, streams.value.log)
    },
  )

lazy val app = project.dependsOn(core)
