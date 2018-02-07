package coursier.cli.spark

import java.io.{File, FileInputStream, FileOutputStream}
import java.math.BigInteger
import java.security.MessageDigest
import java.util.jar.{Attributes, JarFile, JarOutputStream, Manifest}
import java.util.regex.Pattern
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import coursier.Cache
import coursier.cli.Helper
import coursier.cli.options.CommonOptions
import coursier.cli.util.Zip
import coursier.internal.FileUtil

import scala.collection.mutable
import scalaz.\/-

object Assembly {

  sealed abstract class Rule extends Product with Serializable

  object Rule {
    sealed abstract class PathRule extends Rule {
      def path: String
    }

    final case class Exclude(path: String) extends PathRule
    final case class ExcludePattern(path: Pattern) extends Rule

    object ExcludePattern {
      def apply(s: String): ExcludePattern =
        ExcludePattern(Pattern.compile(s))
    }

    // TODO Accept a separator: Array[Byte] argument in these
    // (to separate content with a line return in particular)
    final case class Append(path: String) extends PathRule
    final case class AppendPattern(path: Pattern) extends Rule

    object AppendPattern {
      def apply(s: String): AppendPattern =
        AppendPattern(Pattern.compile(s))
    }
  }

  def make(jars: Seq[File], output: File, rules: Seq[Rule]): Unit = {

    val rulesMap = rules.collect { case r: Rule.PathRule => r.path -> r }.toMap
    val excludePatterns = rules.collect { case Rule.ExcludePattern(p) => p }
    val appendPatterns = rules.collect { case Rule.AppendPattern(p) => p }

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

          for ((ent, content) <- Zip.zipEntries(zis)) {

            def append() =
              concatenedEntries += ent.getName -> ::((ent, content), concatenedEntries.getOrElse(ent.getName, Nil))

            rulesMap.get(ent.getName) match {
              case Some(Rule.Exclude(_)) =>
              // ignored

              case Some(Rule.Append(_)) =>
                append()

              case None =>
                if (!excludePatterns.exists(_.matcher(ent.getName).matches())) {
                  if (appendPatterns.exists(_.matcher(ent.getName).matches()))
                    append()
                  else if (!ignore(ent.getName)) {
                    ent.setCompressedSize(-1L)
                    zos.putNextEntry(ent)
                    zos.write(content)
                    zos.closeEntry()

                    ignore += ent.getName
                  }
                }
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
    Rule.AppendPattern("META-INF/services/.*"),
    Rule.Exclude("log4j.properties"),
    Rule.Exclude(JarFile.MANIFEST_NAME),
    Rule.ExcludePattern("META-INF/.*\\.[sS][fF]"),
    Rule.ExcludePattern("META-INF/.*\\.[dD][sS][aA]"),
    Rule.ExcludePattern("META-INF/.*\\.[rR][sS][aA]")
  )

  def sparkBaseDependencies(
    scalaVersion: String,
    sparkVersion: String,
    yarnVersion: String
  ) =
    if (sparkVersion.startsWith("2."))
      Seq(
        s"org.apache.spark::spark-hive-thriftserver:$sparkVersion",
        s"org.apache.spark::spark-repl:$sparkVersion",
        s"org.apache.spark::spark-hive:$sparkVersion",
        s"org.apache.spark::spark-graphx:$sparkVersion",
        s"org.apache.spark::spark-mllib:$sparkVersion",
        s"org.apache.spark::spark-streaming:$sparkVersion",
        s"org.apache.spark::spark-yarn:$sparkVersion",
        s"org.apache.spark::spark-sql:$sparkVersion",
        s"org.apache.hadoop:hadoop-client:$yarnVersion",
        s"org.apache.hadoop:hadoop-yarn-server-web-proxy:$yarnVersion",
        s"org.apache.hadoop:hadoop-yarn-server-nodemanager:$yarnVersion"
      )
    else
      Seq(
        s"org.apache.spark:spark-core_$scalaVersion:$sparkVersion",
        s"org.apache.spark:spark-bagel_$scalaVersion:$sparkVersion",
        s"org.apache.spark:spark-mllib_$scalaVersion:$sparkVersion",
        s"org.apache.spark:spark-streaming_$scalaVersion:$sparkVersion",
        s"org.apache.spark:spark-graphx_$scalaVersion:$sparkVersion",
        s"org.apache.spark:spark-sql_$scalaVersion:$sparkVersion",
        s"org.apache.spark:spark-repl_$scalaVersion:$sparkVersion",
        s"org.apache.spark:spark-yarn_$scalaVersion:$sparkVersion"
      )

  def sparkJarsHelper(
    scalaVersion: String,
    sparkVersion: String,
    yarnVersion: String,
    default: Boolean,
    extraDependencies: Seq[String],
    options: CommonOptions
  ): Helper = {

    val base = if (default) sparkBaseDependencies(scalaVersion, sparkVersion, yarnVersion) else Seq()
    new Helper(options, extraDependencies ++ base)
  }

  def sparkJars(
    scalaVersion: String,
    sparkVersion: String,
    yarnVersion: String,
    default: Boolean,
    extraDependencies: Seq[String],
    options: CommonOptions,
    artifactTypes: Set[String]
  ): Seq[File] = {

    val helper = sparkJarsHelper(scalaVersion, sparkVersion, yarnVersion, default, extraDependencies, options)

    helper.fetch(sources = false, javadoc = false, artifactTypes = artifactTypes)
  }

  def spark(
    scalaVersion: String,
    sparkVersion: String,
    yarnVersion: String,
    default: Boolean,
    extraDependencies: Seq[String],
    options: CommonOptions,
    artifactTypes: Set[String],
    checksumSeed: Array[Byte] = "v1".getBytes("UTF-8")
  ): Either[String, (File, Seq[File])] = {

    val helper = sparkJarsHelper(scalaVersion, sparkVersion, yarnVersion, default, extraDependencies, options)

    val artifacts = helper.artifacts(sources = false, javadoc = false, artifactTypes = artifactTypes)
    val jars = helper.fetch(sources = false, javadoc = false, artifactTypes = artifactTypes)

    val checksums = artifacts.map { a =>
      val f = a.checksumUrls.get("SHA-1") match {
        case Some(url) =>
          Cache.localFile(url, helper.cache, a.authentication.map(_.user))
        case None =>
          throw new Exception(s"SHA-1 file not found for ${a.url}")
      }

      val sumOpt = Cache.parseRawChecksum(FileUtil.readAllBytes(f))

      sumOpt match {
        case Some(sum) =>
          val s = sum.toString(16)
          "0" * (40 - s.length) + s
        case None =>
          throw new Exception(s"Cannot read SHA-1 sum from $f")
      }
    }


    val md = MessageDigest.getInstance("SHA-1")

    md.update(checksumSeed)

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
        FileUtil.atomicMove(tmpDest, dest)
        \/-((dest, jars))
      }.leftMap(_.describe).toEither
  }

}
