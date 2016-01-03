package coursier
package cli

import java.io.{ByteArrayOutputStream, FileOutputStream, File, IOException}
import java.net.URLClassLoader
import java.nio.file.{ Files => NIOFiles }
import java.nio.file.attribute.{FileTime, PosixFilePermission}
import java.util.Properties
import java.util.zip.{ZipEntry, ZipOutputStream, ZipInputStream, ZipFile}

import caseapp._
import coursier.util.ClasspathFilter

case class CommonOptions(
  @HelpMessage("Keep optional dependencies (Maven)")
    keepOptional: Boolean,
  @HelpMessage("Download mode (default: missing, that is fetch things missing from cache)")
  @ValueDescription("offline|update-changing|update|missing|force")
  @ExtraName("m")
    mode: String = "missing",
  @HelpMessage("Quiet output")
  @ExtraName("q")
    quiet: Boolean,
  @HelpMessage("Increase verbosity (specify several times to increase more)")
  @ExtraName("v")
    verbose: List[Unit],
  @HelpMessage("Maximum number of resolution iterations (specify a negative value for unlimited, default: 100)")
  @ExtraName("N")
    maxIterations: Int = 100,
  @HelpMessage("Repositories - for multiple repositories, separate with comma and/or repeat this option (e.g. -r central,ivy2local -r sonatype-snapshots, or equivalently -r central,ivy2local,sonatype-snapshots)")
  @ExtraName("r")
    repository: List[String],
  @HelpMessage("Do not add default repositories (~/.ivy2/local, and Central)")
    noDefault: Boolean = false,
  @HelpMessage("Modify names in Maven repository paths for SBT plugins")
    sbtPluginHack: Boolean = false,
  @HelpMessage("Force module version")
  @ValueDescription("organization:name:forcedVersion")
  @ExtraName("V")
    forceVersion: List[String],
  @HelpMessage("Maximum number of parallel downloads (default: 6)")
  @ExtraName("n")
    parallel: Int = 6,
  @Recurse
    cacheOptions: CacheOptions
) {
  val verbose0 = verbose.length - (if (quiet) 1 else 0)
}

case class CacheOptions(
  @HelpMessage("Cache directory (defaults to environment variable COURSIER_CACHE or ~/.coursier/cache/v1)")
  @ExtraName("C")
    cache: String = Cache.defaultBase.toString
)

sealed trait CoursierCommand extends Command

case class Fetch(
  @HelpMessage("Fetch source artifacts")
  @ExtraName("S")
    sources: Boolean,
  @HelpMessage("Fetch javadoc artifacts")
  @ExtraName("D")
    javadoc: Boolean,
  @HelpMessage("Print java -cp compatible output")
  @ExtraName("p")
    classpath: Boolean,
  @Recurse
    common: CommonOptions
) extends CoursierCommand {

  val helper = new Helper(common, remainingArgs)

  val files0 = helper.fetch(sources = sources, javadoc = javadoc)

  val out =
    if (classpath)
      files0
        .map(_.toString)
        .mkString(File.pathSeparator)
    else
      files0
        .map(_.toString)
        .mkString("\n")

  println(out)

}

case class Launch(
  @ExtraName("M")
  @ExtraName("main")
    mainClass: String,
  @ExtraName("c")
  @HelpMessage("Assume coursier is a dependency of the launched app, and share the coursier dependency of the launcher with it - allows the launched app to get the resolution that launched it via ResolutionClassLoader")
    addCoursier: Boolean,
  @Recurse
    common: CommonOptions
) extends CoursierCommand {

  val (rawDependencies, extraArgs) = {
    val idxOpt = Some(remainingArgs.indexOf("--")).filter(_ >= 0)
    idxOpt.fold((remainingArgs, Seq.empty[String])) { idx =>
      val (l, r) = remainingArgs.splitAt(idx)
      assert(r.nonEmpty)
      (l, r.tail)
    }
  }

  val extraForceVersions =
    if (addCoursier)
      ???
    else
      Seq.empty[String]

  val dontFilterOut =
    if (addCoursier) {
      val url = classOf[coursier.core.Resolution].getProtectionDomain.getCodeSource.getLocation

      if (url.getProtocol == "file")
        Seq(new File(url.getPath))
      else {
        Console.err.println(s"Cannot get the location of the JAR of coursier ($url not a file URL)")
        sys.exit(255)
      }
    } else
      Seq.empty[File]

  val helper = new Helper(
    common.copy(forceVersion = common.forceVersion ++ extraForceVersions),
    rawDependencies
  )

  val files0 = helper.fetch(sources = false, javadoc = false)

  val cl = new URLClassLoader(
    files0.map(_.toURI.toURL).toArray,
    new ClasspathFilter(
      Thread.currentThread().getContextClassLoader,
      Coursier.baseCp.map(new File(_)).toSet -- dontFilterOut,
      exclude = true
    )
  )

  val mainClass0 =
    if (mainClass.nonEmpty) mainClass
    else {
      val mainClasses = Helper.mainClasses(cl)

      val mainClass =
        if (mainClasses.isEmpty) {
          Helper.errPrintln("No main class found. Specify one with -M or --main.")
          sys.exit(255)
        } else if (mainClasses.size == 1) {
          val (_, mainClass) = mainClasses.head
          mainClass
        } else {
          // Trying to get the main class of the first artifact
          val mainClassOpt = for {
            (module, _) <- helper.moduleVersions.headOption
            mainClass <- mainClasses.collectFirst {
              case ((org, name), mainClass)
                if org == module.organization && (
                  module.name == name ||
                    module.name.startsWith(name + "_") // Ignore cross version suffix
                ) =>
                mainClass
            }
          } yield mainClass

          mainClassOpt.getOrElse {
            Helper.errPrintln(s"Cannot find default main class. Specify one with -M or --main.")
            sys.exit(255)
          }
        }

      mainClass
    }

  val cls =
    try cl.loadClass(mainClass0)
    catch { case e: ClassNotFoundException =>
      Helper.errPrintln(s"Error: class $mainClass0 not found")
      sys.exit(255)
    }
  val method =
    try cls.getMethod("main", classOf[Array[String]])
    catch { case e: NoSuchMethodError =>
      Helper.errPrintln(s"Error: method main not found in $mainClass0")
      sys.exit(255)
    }

  if (common.verbose0 >= 1)
    Helper.errPrintln(s"Launching $mainClass0 ${extraArgs.mkString(" ")}")
  else if (common.verbose0 == 0)
    Helper.errPrintln(s"Launching")

  Thread.currentThread().setContextClassLoader(cl)
  method.invoke(null, extraArgs.toArray)
}

case class Bootstrap(
  @ExtraName("M")
  @ExtraName("main")
    mainClass: String,
  @ExtraName("o")
    output: String = "bootstrap",
  @ExtraName("D")
    downloadDir: String,
  @ExtraName("f")
    force: Boolean,
  @HelpMessage(s"Internal use - prepend base classpath options to arguments (like -B jar1 -B jar2 etc.)")
  @ExtraName("b")
    prependClasspath: Boolean,
  @HelpMessage("Set environment variables in the generated launcher. No escaping is done. Value is simply put between quotes in the launcher preamble.")
  @ValueDescription("key=value")
  @ExtraName("P")
    property: List[String],
  @Recurse
    common: CommonOptions
) extends CoursierCommand {

  import scala.collection.JavaConverters._

  if (mainClass.isEmpty) {
    Console.err.println(s"Error: no main class specified. Specify one with -M or --main")
    sys.exit(255)
  }

  if (downloadDir.isEmpty) {
    Console.err.println(s"Error: no download dir specified. Specify one with -D or --download-dir")
    Console.err.println("E.g. -D \"\\$HOME/.app-name/jars\"")
    sys.exit(255)
  }

  val (validProperties, wrongProperties) = property.partition(_.contains("="))
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

  val output0 = new File(output)
  if (!force && output0.exists()) {
    Console.err.println(s"Error: $output already exists, use -f option to force erasing it.")
    sys.exit(1)
  }

  def zipEntries(zipStream: ZipInputStream): Iterator[(ZipEntry, Array[Byte])] =
    new Iterator[(ZipEntry, Array[Byte])] {
      var nextEntry = Option.empty[ZipEntry]
      def update() =
        nextEntry = Option(zipStream.getNextEntry)

      update()

      def hasNext = nextEntry.nonEmpty
      def next() = {
        val ent = nextEntry.get
        val data = Platform.readFullySync(zipStream)

        update()

        (ent, data)
      }
    }


  val helper = new Helper(common, remainingArgs)

  val artifacts = helper.res.artifacts

  val urls = artifacts.map(_.url)

  val unrecognized = urls.filter(s => !s.startsWith("http://") && !s.startsWith("https://"))
  if (unrecognized.nonEmpty)
    Console.err.println(s"Warning: non HTTP URLs:\n${unrecognized.mkString("\n")}")

  val buffer = new ByteArrayOutputStream()

  val bootstrapZip = new ZipInputStream(Thread.currentThread().getContextClassLoader.getResourceAsStream("bootstrap.jar"))
  val outputZip = new ZipOutputStream(buffer)

  for ((ent, data) <- zipEntries(bootstrapZip)) {
    outputZip.putNextEntry(ent)
    outputZip.write(data)
    outputZip.closeEntry()
  }

  val time = FileTime.fromMillis(System.currentTimeMillis())

  val jarListEntry = new ZipEntry("bootstrap-jar-urls")
  jarListEntry.setCreationTime(time)
  jarListEntry.setLastAccessTime(time)
  jarListEntry.setLastModifiedTime(time)

  outputZip.putNextEntry(jarListEntry)
  outputZip.write(urls.mkString("\n").getBytes("UTF-8"))
  outputZip.closeEntry()

  val propsEntry = new ZipEntry("bootstrap.properties")
  propsEntry.setCreationTime(time)
  propsEntry.setLastAccessTime(time)
  propsEntry.setLastModifiedTime(time)

  val properties = new Properties()
  properties.setProperty("bootstrap.mainClass", mainClass)
  properties.setProperty("bootstrap.jarDir", downloadDir)
  properties.setProperty("bootstrap.prependClasspath", prependClasspath.toString)

  outputZip.putNextEntry(propsEntry)
  properties.store(outputZip, "")
  outputZip.closeEntry()

  outputZip.close()


  val shellPreamble = Seq(
    "#!/usr/bin/env sh",
    "exec java -jar \"$0\" \"$@\""
  ).mkString("", "\n", "\n")

  try NIOFiles.write(output0.toPath, shellPreamble.getBytes("UTF-8") ++ buffer.toByteArray)
  catch { case e: IOException =>
    Console.err.println(s"Error while writing $output0: ${e.getMessage}")
    sys.exit(1)
  }


  try {
    val perms = NIOFiles.getPosixFilePermissions(output0.toPath).asScala.toSet

    var newPerms = perms
    if (perms(PosixFilePermission.OWNER_READ))
      newPerms += PosixFilePermission.OWNER_EXECUTE
    if (perms(PosixFilePermission.GROUP_READ))
      newPerms += PosixFilePermission.GROUP_EXECUTE
    if (perms(PosixFilePermission.OTHERS_READ))
      newPerms += PosixFilePermission.OTHERS_EXECUTE

    if (newPerms != perms)
      NIOFiles.setPosixFilePermissions(
        output0.toPath,
        newPerms.asJava
      )
  } catch {
    case e: UnsupportedOperationException =>
      // Ignored
    case e: IOException =>
      Console.err.println(s"Error while making $output0 executable: ${e.getMessage}")
      sys.exit(1)
  }

}

case class BaseCommand(
  @Hidden
  @ExtraName("B")
    baseCp: List[String]
) extends Command {
  Coursier.baseCp = baseCp

  // FIXME Should be in a trait in case-app
  override def setCommand(cmd: Option[Either[String, String]]): Unit = {
    if (cmd.isEmpty) {
      // FIXME Print available commands too?
      Console.err.println("Error: no command specified")
      sys.exit(255)
    }
    super.setCommand(cmd)
  }
}

object Coursier extends CommandAppOfWithBase[BaseCommand, CoursierCommand] {
  override def appName = "Coursier"
  override def progName = "coursier"

  private[coursier] var baseCp = Seq.empty[String]
}
