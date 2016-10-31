package coursier.cli.spark

import java.io.{File, FileInputStream, FileOutputStream}
import java.math.BigInteger
import java.nio.file.{Files, StandardCopyOption}
import java.security.MessageDigest
import java.util.jar.{Attributes, JarFile, JarOutputStream, Manifest}
import java.util.regex.Pattern
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import coursier.Cache
import coursier.cli.{CommonOptions, Helper}
import coursier.cli.util.Zip

import scala.collection.mutable
import scalaz.\/-

object Assembly {

  sealed abstract class Rule extends Product with Serializable

  object Rule {
    sealed abstract class PathRule extends Rule {
      def path: String
    }

    case class Exclude(path: String) extends PathRule
    case class Append(path: String) extends PathRule

    case class ExcludePattern(path: Pattern) extends Rule

    object ExcludePattern {
      def apply(s: String): ExcludePattern =
        ExcludePattern(Pattern.compile(s))
    }
  }

  def make(jars: Seq[File], output: File, rules: Seq[Rule]): Unit = {

    val rulesMap = rules.collect { case r: Rule.PathRule => r.path -> r }.toMap
    val excludePatterns = rules.collect { case Rule.ExcludePattern(p) => p }

    val manifest = new Manifest
    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")

    var fos: FileOutputStream = null
    var zos: ZipOutputStream = null

    try {
      fos = new FileOutputStream(output)
      zos = new JarOutputStream(fos, manifest)

      val concatenedEntries = new mutable.HashMap[String, ::[(ZipEntry, Array[Byte])]]

      var ignore = Set.empty[String]

      for (jar <- jars) {
        var fis: FileInputStream = null
        var zis: ZipInputStream = null

        try {
          fis = new FileInputStream(jar)
          zis = new ZipInputStream(fis)

          for ((ent, content) <- Zip.zipEntries(zis))
            rulesMap.get(ent.getName) match {
              case Some(Rule.Exclude(_)) =>
              // ignored

              case Some(Rule.Append(path)) =>
                concatenedEntries += path -> ::((ent, content), concatenedEntries.getOrElse(path, Nil))

              case None =>
                if (!excludePatterns.exists(_.matcher(ent.getName).matches()) && !ignore(ent.getName)) {
                  ent.setCompressedSize(-1L)
                  zos.putNextEntry(ent)
                  zos.write(content)
                  zos.closeEntry()

                  ignore += ent.getName
                }
            }

        } finally {
          if (zis != null)
            zis.close()
          if (fis != null)
            fis.close()
        }
      }

      for ((_, entries) <- concatenedEntries) {
        val (ent, _) = entries.head

        ent.setCompressedSize(-1L)

        if (entries.tail.nonEmpty)
          ent.setSize(entries.map(_._2.length).sum)

        zos.putNextEntry(ent)
        // for ((_, b) <- entries.reverse)
        //  zos.write(b)
        zos.write(entries.reverse.toArray.flatMap(_._2))
        zos.closeEntry()
      }
    } finally {
      if (zos != null)
        zos.close()
      if (fos != null)
        fos.close()
    }
  }

  val assemblyRules = Seq[Rule](
    Rule.Append("META-INF/services/org.apache.hadoop.fs.FileSystem"),
    Rule.Append("reference.conf"),
    Rule.Exclude("log4j.properties"),
    Rule.Exclude(JarFile.MANIFEST_NAME),
    Rule.ExcludePattern("META-INF/.*\\.[sS][fF]"),
    Rule.ExcludePattern("META-INF/.*\\.[dD][sS][aA]"),
    Rule.ExcludePattern("META-INF/.*\\.[rR][sS][aA]")
  )

  def sparkAssemblyDependencies(
    scalaVersion: String,
    sparkVersion: String
  ) = Seq(
    s"org.apache.spark:spark-core_$scalaVersion:$sparkVersion",
    s"org.apache.spark:spark-bagel_$scalaVersion:$sparkVersion",
    s"org.apache.spark:spark-mllib_$scalaVersion:$sparkVersion",
    s"org.apache.spark:spark-streaming_$scalaVersion:$sparkVersion",
    s"org.apache.spark:spark-graphx_$scalaVersion:$sparkVersion",
    s"org.apache.spark:spark-sql_$scalaVersion:$sparkVersion",
    s"org.apache.spark:spark-repl_$scalaVersion:$sparkVersion",
    s"org.apache.spark:spark-yarn_$scalaVersion:$sparkVersion"
  )

  def spark(
    scalaVersion: String,
    sparkVersion: String,
    noDefault: Boolean,
    extraDependencies: Seq[String],
    options: CommonOptions,
    artifactTypes: Set[String] = Set("jar")
  ): Either[String, (File, Seq[File])] = {

    val base = if (noDefault) Seq() else sparkAssemblyDependencies(scalaVersion, sparkVersion)
    val helper = new Helper(options, extraDependencies ++ base)

    val artifacts = helper.artifacts(sources = false, javadoc = false, artifactTypes = artifactTypes)
    val jars = helper.fetch(sources = false, javadoc = false, artifactTypes = artifactTypes)

    val checksums = artifacts.map { a =>
      val f = a.checksumUrls.get("SHA-1") match {
        case Some(url) =>
          Cache.localFile(url, helper.cache, a.authentication.map(_.user))
        case None =>
          throw new Exception(s"SHA-1 file not found for ${a.url}")
      }

      val sumOpt = Cache.parseChecksum(
        new String(Files.readAllBytes(f.toPath), "UTF-8")
      )

      sumOpt match {
        case Some(sum) =>
          val s = sum.toString(16)
          "0" * (40 - s.length) + s
        case None =>
          throw new Exception(s"Cannot read SHA-1 sum from $f")
      }
    }


    val md = MessageDigest.getInstance("SHA-1")

    for (c <- checksums.sorted) {
      val b = c.getBytes("UTF-8")
      md.update(b, 0, b.length)
    }

    val digest = md.digest()
    val calculatedSum = new BigInteger(1, digest)
    val s = calculatedSum.toString(16)

    val sum = "0" * (40 - s.length) + s

    val destPath = Seq(
      sys.props("user.home"),
      ".coursier",
      "spark-assemblies",
      s"scala_${scalaVersion}_spark_$sparkVersion",
      sum,
      "spark-assembly.jar"
    ).mkString("/")

    val dest = new File(destPath)

    def success = Right((dest, jars))

    if (dest.exists())
      success
    else
      Cache.withLockFor(helper.cache, dest) {
        dest.getParentFile.mkdirs()
        val tmpDest = new File(dest.getParentFile, s".${dest.getName}.part")
        // FIXME Acquire lock on tmpDest
        Assembly.make(jars, tmpDest, assemblyRules)
        Files.move(tmpDest.toPath, dest.toPath, StandardCopyOption.ATOMIC_MOVE)
        \/-((dest, jars))
      }.leftMap(_.describe).toEither
  }

}
