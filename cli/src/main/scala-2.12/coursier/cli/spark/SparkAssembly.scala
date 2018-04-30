package coursier.cli.spark

import java.io.{File, FileOutputStream}
import java.math.BigInteger
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, StandardCopyOption}
import java.security.MessageDigest
import java.util.jar.JarFile

import coursier.Cache
import coursier.cli.Helper
import coursier.cli.options.CommonOptions
import coursier.cli.util.Assembly

object SparkAssembly {

  val assemblyRules = Seq[Assembly.Rule](
    Assembly.Rule.Append("META-INF/services/org.apache.hadoop.fs.FileSystem"),
    Assembly.Rule.Append("reference.conf"),
    Assembly.Rule.AppendPattern("META-INF/services/.*"),
    Assembly.Rule.Exclude("log4j.properties"),
    Assembly.Rule.Exclude(JarFile.MANIFEST_NAME),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[sS][fF]"),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[dD][sS][aA]"),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[rR][sS][aA]")
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
    checksumSeed: Array[Byte] = "v1".getBytes(UTF_8),
    localArtifactsShouldBeCached: Boolean = false
  ): Either[String, (File, Seq[File])] = {

    val helper = sparkJarsHelper(scalaVersion, sparkVersion, yarnVersion, default, extraDependencies, options)

    val artifacts = helper.artifacts(sources = false, javadoc = false, artifactTypes = artifactTypes)
    val jars = helper.fetch(sources = false, javadoc = false, artifactTypes = artifactTypes)

    val checksums = artifacts.map { a =>
      val f = a.checksumUrls.get("SHA-1") match {
        case Some(url) =>
          Cache.localFile(url, helper.cache, a.authentication.map(_.user), localArtifactsShouldBeCached)
        case None =>
          throw new Exception(s"SHA-1 file not found for ${a.url}")
      }

      val sumOpt = Cache.parseRawChecksum(Files.readAllBytes(f.toPath))

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
      val b = c.getBytes(UTF_8)
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
        var fos: FileOutputStream = null
        try {
          fos = new FileOutputStream(tmpDest)
          Assembly.make(jars, fos, Nil, assemblyRules)
        } finally {
          if (fos != null)
            fos.close()
        }
        Files.move(tmpDest.toPath, dest.toPath, StandardCopyOption.ATOMIC_MOVE)
        Right((dest, jars))
      }.left.map(_.describe)
  }

}
