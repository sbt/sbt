package coursier
package cli

import java.io.{ FileInputStream, ByteArrayOutputStream, File, IOException }
import java.net.URLClassLoader
import java.nio.file.{ Files => NIOFiles }
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties
import java.util.zip.{ ZipEntry, ZipOutputStream, ZipInputStream }

import caseapp.{ HelpMessage => Help, ValueDescription => Value, ExtraName => Short, _ }
import coursier.util.Parse

import scala.annotation.tailrec
import scala.language.reflectiveCalls
import scala.util.Try

case class CommonOptions(
  @Help("Keep optional dependencies (Maven)")
    keepOptional: Boolean,
  @Help("Download mode (default: missing, that is fetch things missing from cache)")
  @Value("offline|update-changing|update|missing|force")
  @Short("m")
    mode: String = "default",
  @Help("Quiet output")
  @Short("q")
    quiet: Boolean,
  @Help("Increase verbosity (specify several times to increase more)")
  @Short("v")
    verbose: List[Unit],
  @Help("Maximum number of resolution iterations (specify a negative value for unlimited, default: 100)")
  @Short("N")
    maxIterations: Int = 100,
  @Help("Repositories - for multiple repositories, separate with comma and/or repeat this option (e.g. -r central,ivy2local -r sonatype-snapshots, or equivalently -r central,ivy2local,sonatype-snapshots)")
  @Short("r")
    repository: List[String],
  @Help("Do not add default repositories (~/.ivy2/local, and Central)")
    noDefault: Boolean = false,
  @Help("Modify names in Maven repository paths for SBT plugins")
    sbtPluginHack: Boolean = false,
  @Help("Drop module attributes starting with 'info.' - these are sometimes used by projects built with SBT")
    dropInfoAttr: Boolean = false,
  @Help("Force module version")
  @Value("organization:name:forcedVersion")
  @Short("V")
    forceVersion: List[String],
  @Help("Exclude module")
  @Value("organization:name")
  @Short("E")
    exclude: List[String],
  @Help("Consider provided dependencies to be intransitive. Applies to all the provided dependencies.")
    intransitive: Boolean,
  @Help("Classifiers that should be fetched")
  @Value("classifier1,classifier2,...")
  @Short("C")
    classifier: List[String],
  @Help("Maximum number of parallel downloads (default: 6)")
  @Short("n")
    parallel: Int = 6,
  @Help("Checksums")
  @Value("checksum1,checksum2,... - end with none to allow for no checksum validation if none are available")
    checksum: List[String],
  @Recurse
    cacheOptions: CacheOptions
) {
  val verbose0 = verbose.length - (if (quiet) 1 else 0)
  lazy val classifier0 = classifier.flatMap(_.split(',')).filter(_.nonEmpty)
}

case class CacheOptions(
  @Help("Cache directory (defaults to environment variable COURSIER_CACHE or ~/.coursier/cache/v1)")
  @Short("C")
    cache: String = Cache.defaultBase.toString
)

sealed abstract class CoursierCommand extends Command

case class Resolve(
  @Recurse
    common: CommonOptions
) extends CoursierCommand {

  // the `val helper = ` part is needed because of DelayedInit it seems
  val helper = new Helper(common, remainingArgs, printResultStdout = true)

}

case class Fetch(
  @Help("Fetch source artifacts")
  @Short("S")
    sources: Boolean,
  @Help("Fetch javadoc artifacts")
  @Short("D")
    javadoc: Boolean,
  @Help("Print java -cp compatible output")
  @Short("p")
    classpath: Boolean,
  @Help("Fetch artifacts even if the resolution is errored")
    force: Boolean,
  @Recurse
    common: CommonOptions
) extends CoursierCommand {

  val helper = new Helper(common, remainingArgs, ignoreErrors = force)

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

case class IsolatedLoaderOptions(
  @Value("target:dependency")
  @Short("I")
    isolated: List[String],
  @Help("Comma-separated isolation targets")
  @Short("i")
    isolateTarget: List[String]
) {

  def anyIsolatedDep = isolateTarget.nonEmpty || isolated.nonEmpty

  lazy val targets = {
    val l = isolateTarget.flatMap(_.split(',')).filter(_.nonEmpty)
    val (invalid, valid) = l.partition(_.contains(":"))
    if (invalid.nonEmpty) {
      Console.err.println(s"Invalid target IDs:")
      for (t <- invalid)
        Console.err.println(s"  $t")
      sys.exit(255)
    }
    if (valid.isEmpty)
      Array("default")
    else
      valid.toArray
  }

  lazy val (validIsolated, unrecognizedIsolated) = isolated.partition(s => targets.exists(t => s.startsWith(t + ":")))

  def check() = {
    if (unrecognizedIsolated.nonEmpty) {
      Console.err.println(s"Unrecognized isolation targets in:")
      for (i <- unrecognizedIsolated)
        Console.err.println(s"  $i")
      sys.exit(255)
    }
  }

  lazy val rawIsolated = validIsolated.map { s =>
    val Array(target, dep) = s.split(":", 2)
    target -> dep
  }

  lazy val isolatedModuleVersions = rawIsolated.groupBy { case (t, _) => t }.map {
    case (t, l) =>
      val (errors, modVers) = Parse.moduleVersions(l.map { case (_, d) => d })

      if (errors.nonEmpty) {
        errors.foreach(Console.err.println)
        sys.exit(255)
      }

      t -> modVers
  }

  lazy val isolatedDeps = isolatedModuleVersions.map {
    case (t, l) =>
      t -> l.map {
        case (mod, ver) =>
          Dependency(mod, ver, configuration = "runtime")
      }
  }

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
  @Short("M")
  @Short("main")
    mainClass: String,
  @Recurse
    isolated: IsolatedLoaderOptions,
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

  val helper = new Helper(
    common,
    rawDependencies ++ isolated.rawIsolated.map { case (_, dep) => dep }
  )


  val files0 = helper.fetch(sources = false, javadoc = false)

  val contextLoader = Thread.currentThread().getContextClassLoader

  val parentLoader0: ClassLoader =
    if (Try(contextLoader.loadClass("coursier.cli.Launch")).isSuccess)
      Launch.mainClassLoader(contextLoader)
        .flatMap(cl => Option(cl.getParent))
        .getOrElse {
          if (common.verbose0 >= 0)
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
    if (isolated.isolated.isEmpty)
      (parentLoader0, files0)
    else {
      val (isolatedLoader, filteredFiles0) = isolated.targets.foldLeft((parentLoader0, files0)) {
        case ((parent, files0), target) =>

          // FIXME These were already fetched above
          val isolatedFiles = helper.fetch(
            sources = false,
            javadoc = false,
            subset = isolated.isolatedDeps.getOrElse(target, Seq.empty).toSet
          )

          if (common.verbose0 >= 1) {
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

      if (common.verbose0 >= 1) {
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
    if (mainClass.nonEmpty) mainClass
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

  if (common.verbose0 >= 1)
    Helper.errPrintln(s"Launching $mainClass0 ${extraArgs.mkString(" ")}")
  else if (common.verbose0 == 0)
    Helper.errPrintln(s"Launching")

  Thread.currentThread().setContextClassLoader(loader)
  method.invoke(null, extraArgs.toArray)
}

case class Bootstrap(
  @Short("M")
  @Short("main")
    mainClass: String,
  @Short("o")
    output: String = "bootstrap",
  @Short("D")
    downloadDir: String,
  @Short("f")
    force: Boolean,
  @Help("Generate a standalone launcher, with all JARs included, instead of one downloading its dependencies on startup.")
  @Short("s")
    standalone: Boolean,
  @Help("Set Java properties in the generated launcher.")
  @Value("key=value")
  @Short("P")
    property: List[String],
  @Recurse
    isolated: IsolatedLoaderOptions,
  @Recurse
    common: CommonOptions
) extends CoursierCommand {

  import scala.collection.JavaConverters._

  if (mainClass.isEmpty) {
    Console.err.println(s"Error: no main class specified. Specify one with -M or --main")
    sys.exit(255)
  }

  if (!standalone && downloadDir.isEmpty) {
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

  val (_, isolatedArtifactFiles) =
    isolated.targets.foldLeft((Vector.empty[String], Map.empty[String, (Seq[String], Seq[File])])) {
      case ((done, acc), target) =>
        val subRes = helper.res.subset(isolated.isolatedDeps.getOrElse(target, Nil).toSet)
        val subArtifacts = subRes.artifacts.map(_.url)

        val filteredSubArtifacts = subArtifacts.diff(done)

        def subFiles0 = helper.fetch(
          sources = false,
          javadoc = false,
          subset = isolated.isolatedDeps.getOrElse(target, Seq.empty).toSet
        )

        val (subUrls, subFiles) =
          if (standalone)
            (Nil, subFiles0)
          else
            (filteredSubArtifacts, Nil)

        val updatedAcc = acc + (target -> (subUrls, subFiles))

        (done ++ filteredSubArtifacts, updatedAcc)
    }

  val (urls, files) =
    if (standalone)
      (
        Seq.empty[String],
        helper.fetch(sources = false, javadoc = false)
      )
    else
      (
        helper.res.artifacts.map(_.url),
        Seq.empty[File]
      )

  val isolatedUrls = isolatedArtifactFiles.map { case (k, (v, _)) => k -> v }
  val isolatedFiles = isolatedArtifactFiles.map { case (k, (_, v)) => k -> v }

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

  if (isolated.anyIsolatedDep) {
    putStringEntry("bootstrap-isolation-ids", isolated.targets.mkString("\n"))

    for (target <- isolated.targets) {
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
  properties.setProperty("bootstrap.mainClass", mainClass)
  if (!standalone)
    properties.setProperty("bootstrap.jarDir", downloadDir)

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

object Coursier extends CommandAppOf[CoursierCommand] {
  override def appName = "Coursier"
  override def progName = "coursier"
  override def appVersion = coursier.util.Properties.version
}
