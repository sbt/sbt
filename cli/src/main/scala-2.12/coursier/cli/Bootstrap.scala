package coursier
package cli

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import caseapp._
import coursier.cli.options.BootstrapOptions
import coursier.cli.util.Zip
import coursier.internal.FileUtil

import scala.collection.JavaConverters._

object Bootstrap extends CaseApp[BootstrapOptions] {

  def run(options: BootstrapOptions, args: RemainingArgs): Unit = {

    val helper = new Helper(
      options.options.common,
      args.all,
      isolated = options.options.isolated,
      warnBaseLoaderNotFound = false
    )

    val output0 = new File(options.options.output)
    if (!options.options.force && output0.exists()) {
      Console.err.println(s"Error: ${options.options.output} already exists, use -f option to force erasing it.")
      sys.exit(1)
    }

    val mainClass =
      if (options.options.mainClass.isEmpty)
        helper.retainedMainClass
      else
        options.options.mainClass

    if (options.options.native) {

      val files = helper.fetch(
        sources = false,
        javadoc = false,
        artifactTypes = options.artifactOptions.artifactTypes(sources = false, javadoc = false)
      )

      val log: String => Unit =
        if (options.options.common.verbosityLevel >= 0)
          s => Console.err.println(s)
        else
          _ => ()

      val tmpDir = new File(options.options.target)

      try {
        coursier.extra.Native.create(
          mainClass,
          files,
          output0,
          tmpDir,
          log,
          verbosity = options.options.common.verbosityLevel
        )
      } finally {
        if (!options.options.keepTarget)
          coursier.extra.Native.deleteRecursive(tmpDir)
      }
    } else {

      val (validProperties, wrongProperties) = options.options.property.partition(_.contains("="))
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
          case Some(is) => FileUtil.readFully(is)
          case None =>
            Console.err.println(s"Error: bootstrap JAR not found")
            sys.exit(1)
        }

      val isolatedDeps = options.options.isolated.isolatedDeps(options.options.common.scalaVersion)

      val (_, isolatedArtifactFiles) =
        options.options.isolated.targets.foldLeft((Vector.empty[String], Map.empty[String, (Seq[String], Seq[File])])) {
          case ((done, acc), target) =>

            // TODO Add non regression test checking that optional artifacts indeed land in the isolated loader URLs

            val m = helper.fetchMap(
              sources = false,
              javadoc = false,
              artifactTypes = options.artifactOptions.artifactTypes(sources = false, javadoc = false),
              subset = isolatedDeps.getOrElse(target, Seq.empty).toSet
            )

            val (done0, subUrls, subFiles) =
              if (options.options.standalone) {
                val subFiles0 = m.values.toSeq
                (done, Nil, subFiles0)
              } else {
                val filteredSubArtifacts = m.keys.toSeq.diff(done)
                (done ++ filteredSubArtifacts, filteredSubArtifacts, Nil)
              }

            val updatedAcc = acc + (target -> (subUrls, subFiles))

            (done0, updatedAcc)
        }

      val (urls, files) =
        helper.fetchMap(
        sources = false,
        javadoc = false,
        artifactTypes = options.artifactOptions.artifactTypes(sources = false, javadoc = false)
      ).toList.foldLeft((List.empty[String], List.empty[File])){
        case ((urls, files), (url, file)) =>
          if (options.options.standalone) (urls, file :: files)
          else if (url.startsWith("file:/")) (urls, file :: files)
          else (url :: urls, files)
      }

      val isolatedUrls = isolatedArtifactFiles.map { case (k, (v, _)) => k -> v }
      val isolatedFiles = isolatedArtifactFiles.map { case (k, (_, v)) => k -> v }

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
        outputZip.write(content.getBytes(UTF_8))
        outputZip.closeEntry()
      }

      def putEntryFromFile(name: String, f: File): Unit = {
        val entry = new ZipEntry(name)
        entry.setTime(f.lastModified())

        outputZip.putNextEntry(entry)
        outputZip.write(FileUtil.readFully(new FileInputStream(f)))
        outputZip.closeEntry()
      }

      putStringEntry("bootstrap-jar-urls", urls.mkString("\n"))

      if (options.options.isolated.anyIsolatedDep) {
        putStringEntry("bootstrap-isolation-ids", options.options.isolated.targets.mkString("\n"))

        for (target <- options.options.isolated.targets) {
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

      outputZip.putNextEntry(propsEntry)
      properties.store(outputZip, "")
      outputZip.closeEntry()

      outputZip.close()

      // escaping of  javaOpt  possibly a bit loose :-|
      val shellPreamble = Seq(
        "#!/usr/bin/env sh",
        "exec java " + options.options.javaOpt.map(s => "'" + s.replace("'", "\\'") + "'").mkString(" ") + " -jar \"$0\" \"$@\""
      ).mkString("", "\n", "\n")

      try Files.write(output0.toPath, shellPreamble.getBytes(UTF_8) ++ buffer.toByteArray)
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
  }

}
