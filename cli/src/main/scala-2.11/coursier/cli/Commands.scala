package coursier
package cli

import java.io.{ FileInputStream, ByteArrayOutputStream, File, IOException }
import java.net.URLClassLoader
import java.nio.file.{ Files => NIOFiles }
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties
import java.util.zip.{ ZipEntry, ZipOutputStream, ZipInputStream }

import caseapp.{ HelpMessage => Help, ValueDescription => Value, ExtraName => Short, _ }

import scala.annotation.tailrec
import scala.language.reflectiveCalls
import scala.util.Try


sealed abstract class CoursierCommand extends Command

case class Resolve(
  @Recurse
    common: CommonOptions
) extends CoursierCommand {

  // the `val helper = ` part is needed because of DelayedInit it seems
  val helper = new Helper(common, remainingArgs, printResultStdout = true)

}

case class Fetch(
  @Recurse
    options: FetchOptions
) extends CoursierCommand {

  val helper = new Helper(options.common, remainingArgs, ignoreErrors = options.force)

  val files0 = helper.fetch(sources = options.sources, javadoc = options.javadoc)

  val out =
    if (options.classpath)
      files0
        .map(_.toString)
        .mkString(File.pathSeparator)
    else
      files0
        .map(_.toString)
        .mkString("\n")

  println(out)

}

object Launch {

  @tailrec
  def mainClassLoader(cl: ClassLoader): Option[ClassLoader] =
    if (cl == null)
      None
    else {
      val isMainLoader = try {
        val cl0 = cl.asInstanceOf[Object {
          def isBootstrapLoader: Boolean
        }]

        cl0.isBootstrapLoader
      } catch {
        case e: Exception =>
          false
      }

      if (isMainLoader)
        Some(cl)
      else
        mainClassLoader(cl.getParent)
    }

}

case class Launch(
  @Recurse
    options: LaunchOptions
) extends CoursierCommand {

  val (rawDependencies, extraArgs) = {
    val idxOpt = Some(remainingArgs.indexOf("--")).filter(_ >= 0)
    idxOpt.fold((remainingArgs, Seq.empty[String])) { idx =>
      val (l, r) = remainingArgs.splitAt(idx)
      assert(r.nonEmpty)
      (l, r.tail)
    }
  }

  val helper = new Helper(
    options.common,
    rawDependencies ++ options.isolated.rawIsolated.map { case (_, dep) => dep }
  )


  val files0 = helper.fetch(sources = false, javadoc = false)

  val contextLoader = Thread.currentThread().getContextClassLoader

  val parentLoader0: ClassLoader =
    if (Try(contextLoader.loadClass("coursier.cli.Launch")).isSuccess)
      Launch.mainClassLoader(contextLoader)
        .flatMap(cl => Option(cl.getParent))
        .getOrElse {
          if (options.common.verbose0 >= 0)
            Console.err.println(
              "Warning: cannot find the main ClassLoader that launched coursier. " +
              "Was coursier launched by its main launcher? " +
              "The ClassLoader of the application that is about to be launched will be intertwined " +
              "with the one of coursier, which may be a problem if their dependencies conflict."
            )
          contextLoader
        }
    else
      // proguarded -> no risk of conflicts, no need to find a specific ClassLoader
      contextLoader

  val (parentLoader, filteredFiles) =
    if (options.isolated.isolated.isEmpty)
      (parentLoader0, files0)
    else {
      val (isolatedLoader, filteredFiles0) = options.isolated.targets.foldLeft((parentLoader0, files0)) {
        case ((parent, files0), target) =>

          // FIXME These were already fetched above
          val isolatedFiles = helper.fetch(
            sources = false,
            javadoc = false,
            subset = options.isolated.isolatedDeps.getOrElse(target, Seq.empty).toSet
          )

          if (options.common.verbose0 >= 1) {
            Console.err.println(s"Isolated loader files:")
            for (f <- isolatedFiles.map(_.toString).sorted)
              Console.err.println(s"  $f")
          }

          val isolatedLoader = new IsolatedClassLoader(
            isolatedFiles.map(_.toURI.toURL).toArray,
            parent,
            Array(target)
          )

          val filteredFiles0 = files0.filterNot(isolatedFiles.toSet)

          (isolatedLoader, filteredFiles0)
      }

      if (options.common.verbose0 >= 1) {
        Console.err.println(s"Remaining files:")
        for (f <- filteredFiles0.map(_.toString).sorted)
          Console.err.println(s"  $f")
      }

      (isolatedLoader, filteredFiles0)
    }

  val loader = new URLClassLoader(
    filteredFiles.map(_.toURI.toURL).toArray,
    parentLoader
  )

  val mainClass0 =
    if (options.mainClass.nonEmpty) options.mainClass
    else {
      val mainClasses = Helper.mainClasses(loader)

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
            (module, _, _) <- helper.moduleVersionConfigs.headOption
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
    try loader.loadClass(mainClass0)
    catch { case e: ClassNotFoundException =>
      Helper.errPrintln(s"Error: class $mainClass0 not found")
      sys.exit(255)
    }
  val method =
    try cls.getMethod("main", classOf[Array[String]])
    catch { case e: NoSuchMethodException =>
      Helper.errPrintln(s"Error: method main not found in $mainClass0")
      sys.exit(255)
    }

  if (options.common.verbose0 >= 1)
    Helper.errPrintln(s"Launching $mainClass0 ${extraArgs.mkString(" ")}")
  else if (options.common.verbose0 == 0)
    Helper.errPrintln(s"Launching")

  Thread.currentThread().setContextClassLoader(loader)
  method.invoke(null, extraArgs.toArray)
}

case class Bootstrap(
  @Recurse
    options: BootstrapOptions
) extends CoursierCommand {

  import scala.collection.JavaConverters._

  if (options.mainClass.isEmpty) {
    Console.err.println(s"Error: no main class specified. Specify one with -M or --main")
    sys.exit(255)
  }

  if (!options.standalone && options.downloadDir.isEmpty) {
    Console.err.println(s"Error: no download dir specified. Specify one with -D or --download-dir")
    Console.err.println("E.g. -D \"\\$HOME/.app-name/jars\"")
    sys.exit(255)
  }

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


  val helper = new Helper(options.common, remainingArgs)

  val (_, isolatedArtifactFiles) =
    options.isolated.targets.foldLeft((Vector.empty[String], Map.empty[String, (Seq[String], Seq[File])])) {
      case ((done, acc), target) =>
        val subRes = helper.res.subset(options.isolated.isolatedDeps.getOrElse(target, Nil).toSet)
        val subArtifacts = subRes.artifacts.map(_.url)

        val filteredSubArtifacts = subArtifacts.diff(done)

        def subFiles0 = helper.fetch(
          sources = false,
          javadoc = false,
          subset = options.isolated.isolatedDeps.getOrElse(target, Seq.empty).toSet
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
        helper.fetch(sources = false, javadoc = false)
      )
    else
      (
        helper.artifacts(sources = false, javadoc = false).map(_.url),
        Seq.empty[File]
      )

  val isolatedUrls = isolatedArtifactFiles.map { case (k, (v, _)) => k -> v }
  val isolatedFiles = isolatedArtifactFiles.map { case (k, (_, v)) => k -> v }

  val nonHttpUrls = urls.filter(s => !s.startsWith("http://") && !s.startsWith("https://"))
  if (nonHttpUrls.nonEmpty)
    Console.err.println(s"Warning: non HTTP URLs:\n${nonHttpUrls.mkString("\n")}")

  val buffer = new ByteArrayOutputStream()

  val bootstrapZip = new ZipInputStream(Thread.currentThread().getContextClassLoader.getResourceAsStream("bootstrap.jar"))
  val outputZip = new ZipOutputStream(buffer)

  for ((ent, data) <- zipEntries(bootstrapZip)) {
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

  val properties = new Properties()
  properties.setProperty("bootstrap.mainClass", options.mainClass)
  if (!options.standalone)
    properties.setProperty("bootstrap.jarDir", options.downloadDir)

  outputZip.putNextEntry(propsEntry)
  properties.store(outputZip, "")
  outputZip.closeEntry()

  outputZip.close()

  // escaping of  javaOpt  possibly a bit loose :-|
  val shellPreamble = Seq(
    "#!/usr/bin/env sh",
    "exec java -jar " + options.javaOpt.map(s => "'" + s.replace("'", "\\'") + "'").mkString(" ") + " \"$0\" \"$@\""
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
