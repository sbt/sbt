package coursier
package cli

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream, IOException}
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import caseapp._
import coursier.cli.util.Zip
import coursier.internal.FileUtil

final case class Bootstrap(
  @Recurse
    artifactOptions: ArtifactOptions,
  @Recurse
    options: BootstrapOptions
) extends App {

  import scala.collection.JavaConverters._

  val helper = new Helper(
    options.common,
    remainingArgs,
    isolated = options.isolated,
    warnBaseLoaderNotFound = false
  )

  lazy val downloadDir =
    if (options.downloadDir.isEmpty)
      helper.baseDependencies.headOption match {
        case Some(dep) =>
          s"$${user.home}/.coursier/bootstrap/${dep.module.organization}/${dep.module.name}"
        case None =>
          Console.err.println("Error: no dependencies specified.")
          sys.exit(255)
      }
    else
      options.downloadDir

  val (validProperties, wrongProperties) = options.property.partition(_.contains("="))
  if (wrongProperties.nonEmpty) {
    Console.err.println(s"Wrong -P / --property option(s):\n${wrongProperties.mkString("\n")}")
    sys.exit(255)
  }

  val properties0 = validProperties.map { s =>
    val idx = s.indexOf('=')
    assert(idx >= 0)
    (s.take(idx), s.drop(idx + 1))
  }

  val bootstrapJar =
    Option(Thread.currentThread().getContextClassLoader.getResourceAsStream("bootstrap.jar")) match {
      case Some(is) => Cache.readFullySync(is)
      case None =>
        Console.err.println(s"Error: bootstrap JAR not found")
        sys.exit(1)
    }

  val output0 = new File(options.output)
  if (!options.force && output0.exists()) {
    Console.err.println(s"Error: ${options.output} already exists, use -f option to force erasing it.")
    sys.exit(1)
  }

  val isolatedDeps = options.isolated.isolatedDeps(options.common.scalaVersion)

  val (_, isolatedArtifactFiles) =
    options.isolated.targets.foldLeft((Vector.empty[String], Map.empty[String, (Seq[String], Seq[File])])) {
      case ((done, acc), target) =>
        val subRes = helper.res.subset(isolatedDeps.getOrElse(target, Nil).toSet)
        val subArtifacts = subRes.artifacts.map(_.url)

        val filteredSubArtifacts = subArtifacts.diff(done)

        def subFiles0 = helper.fetch(
          sources = false,
          javadoc = false,
          artifactTypes = artifactOptions.artifactTypes(sources = false, javadoc = false),
          subset = isolatedDeps.getOrElse(target, Seq.empty).toSet
        )

        val (subUrls, subFiles) =
          if (options.standalone)
            (Nil, subFiles0)
          else
            (filteredSubArtifacts, Nil)

        val updatedAcc = acc + (target -> (subUrls, subFiles))

        (done ++ filteredSubArtifacts, updatedAcc)
    }

  val (urls, files) =
    if (options.standalone)
      (
        Seq.empty[String],
        helper.fetch(
          sources = false,
          javadoc = false,
          artifactTypes = artifactOptions.artifactTypes(sources = false, javadoc = false)
        )
      )
    else
      (
        helper.artifacts(
          sources = false,
          javadoc = false,
          artifactTypes = artifactOptions.artifactTypes(sources = false, javadoc = false)
        ).map(_.url),
        Seq.empty[File]
      )

  val isolatedUrls = isolatedArtifactFiles.map { case (k, (v, _)) => k -> v }
  val isolatedFiles = isolatedArtifactFiles.map { case (k, (_, v)) => k -> v }

  val nonHttpUrls = urls.filter(s => !s.startsWith("http://") && !s.startsWith("https://"))
  if (nonHttpUrls.nonEmpty)
    Console.err.println(s"Warning: non HTTP URLs:\n${nonHttpUrls.mkString("\n")}")

  val mainClass =
    if (options.mainClass.isEmpty)
      helper.retainedMainClass
    else
      options.mainClass

  val buffer = new ByteArrayOutputStream

  val bootstrapZip = new ZipInputStream(new ByteArrayInputStream(bootstrapJar))
  val outputZip = new ZipOutputStream(buffer)

  for ((ent, data) <- Zip.zipEntries(bootstrapZip)) {
    outputZip.putNextEntry(ent)
    outputZip.write(data)
    outputZip.closeEntry()
  }


  val time = System.currentTimeMillis()

  def putStringEntry(name: String, content: String): Unit = {
    val entry = new ZipEntry(name)
    entry.setTime(time)

    outputZip.putNextEntry(entry)
    outputZip.write(content.getBytes("UTF-8"))
    outputZip.closeEntry()
  }

  def putEntryFromFile(name: String, f: File): Unit = {
    val entry = new ZipEntry(name)
    entry.setTime(f.lastModified())

    outputZip.putNextEntry(entry)
    outputZip.write(Cache.readFullySync(new FileInputStream(f)))
    outputZip.closeEntry()
  }

  putStringEntry("bootstrap-jar-urls", urls.mkString("\n"))

  if (options.isolated.anyIsolatedDep) {
    putStringEntry("bootstrap-isolation-ids", options.isolated.targets.mkString("\n"))

    for (target <- options.isolated.targets) {
      val urls = isolatedUrls.getOrElse(target, Nil)
      val files = isolatedFiles.getOrElse(target, Nil)
      putStringEntry(s"bootstrap-isolation-$target-jar-urls", urls.mkString("\n"))
      putStringEntry(s"bootstrap-isolation-$target-jar-resources", files.map(pathFor).mkString("\n"))
    }
  }

  def pathFor(f: File) = s"jars/${f.getName}"

  for (f <- files)
    putEntryFromFile(pathFor(f), f)

  putStringEntry("bootstrap-jar-resources", files.map(pathFor).mkString("\n"))

  val propsEntry = new ZipEntry("bootstrap.properties")
  propsEntry.setTime(time)

  val properties = new Properties
  properties.setProperty("bootstrap.mainClass", mainClass)
  if (!options.standalone)
    properties.setProperty("bootstrap.jarDir", downloadDir)

  outputZip.putNextEntry(propsEntry)
  properties.store(outputZip, "")
  outputZip.closeEntry()

  outputZip.close()

  // escaping of  javaOpt  possibly a bit loose :-|
  val shellPreamble = Seq(
    "#!/usr/bin/env sh",
    "exec java -jar " + options.javaOpt.map(s => "'" + s.replace("'", "\\'") + "'").mkString(" ") + " \"$0\" \"$@\""
  ).mkString("", "\n", "\n")

  try FileUtil.write(output0, shellPreamble.getBytes("UTF-8") ++ buffer.toByteArray)
  catch { case e: IOException =>
    Console.err.println(s"Error while writing $output0${Option(e.getMessage).fold("")(" (" + _ + ")")}")
    sys.exit(1)
  }


  try {
    val perms = Files.getPosixFilePermissions(output0.toPath).asScala.toSet

    var newPerms = perms
    if (perms(PosixFilePermission.OWNER_READ))
      newPerms += PosixFilePermission.OWNER_EXECUTE
    if (perms(PosixFilePermission.GROUP_READ))
      newPerms += PosixFilePermission.GROUP_EXECUTE
    if (perms(PosixFilePermission.OTHERS_READ))
      newPerms += PosixFilePermission.OTHERS_EXECUTE

    if (newPerms != perms)
      Files.setPosixFilePermissions(
        output0.toPath,
        newPerms.asJava
      )
  } catch {
    case e: UnsupportedOperationException =>
      // Ignored
    case e: IOException =>
      Console.err.println(
        s"Error while making $output0 executable" +
          Option(e.getMessage).fold("")(" (" + _ + ")")
      )
      sys.exit(1)
  }

}
