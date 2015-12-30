package coursier
package cli

import java.io.{ File, IOException }
import java.net.URLClassLoader
import java.nio.file.{ Files => NIOFiles }
import java.nio.file.attribute.PosixFilePermission

import caseapp._
import coursier.util.ClasspathFilter

case class CommonOptions(
  @HelpMessage("Keep optional dependencies (Maven)")
    keepOptional: Boolean,
  @HelpMessage("Download mode (default: missing, that is fetch things missing from cache)")
  @ValueDescription("offline|update-changing|update|missing|force")
  @ExtraName("m")
    mode: String,
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

object CacheOptions {
  def default =
    sys.env.getOrElse(
      "COURSIER_CACHE",
      sys.props("user.home") + "/.coursier/cache"
    )
}

case class CacheOptions(
  @HelpMessage("Cache directory (defaults to environment variable COURSIER_CACHE or ~/.coursier/cache)")
  @ExtraName("C")
    cache: String = CacheOptions.default
)

sealed trait CoursierCommand extends Command

case class Fetch(
  @HelpMessage("Fetch source artifacts")
  @ExtraName("S")
    sources: Boolean,
  @HelpMessage("Fetch javadoc artifacts")
  @ExtraName("D")
    javadoc: Boolean,
  @Recurse
    common: CommonOptions
) extends CoursierCommand {

  val helper = new Helper(common, remainingArgs)

  val files0 = helper.fetch(main = true, sources = false, javadoc = false)

  println(
    files0
      .map(_.toString)
      .mkString("\n")
  )

}

case class Launch(
  @ExtraName("M")
  @ExtraName("main")
    mainClass: String,
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

  val helper = new Helper(common, rawDependencies)

  val files0 = helper.fetch(main = true, sources = false, javadoc = false)

  val cl = new URLClassLoader(
    files0.map(_.toURI.toURL).toArray,
    new ClasspathFilter(
      Thread.currentThread().getContextClassLoader,
      Coursier.baseCp.map(new File(_)).toSet,
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

case class Classpath(
  @Recurse
    common: CommonOptions
) extends CoursierCommand {

  val helper = new Helper(common, remainingArgs)

  val files0 = helper.fetch(main = true, sources = false, javadoc = false)

  Console.out.println(
    files0
      .map(_.toString)
      .mkString(File.pathSeparator)
  )

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
  @ValueDescription("NAME=VALUE")
  @ExtraName("e")
    env: List[String],
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

  val (validEnv, wrongEnv) = env.partition(_.contains("="))
  if (wrongEnv.nonEmpty) {
    Console.err.println(s"Wrong -e / --env option(s):\n${wrongEnv.mkString("\n")}")
    sys.exit(255)
  }

  val env0 = validEnv.map { s =>
    val idx = s.indexOf('=')
    assert(idx >= 0)
    (s.take(idx), s.drop(idx + 1))
  }

  val downloadDir0 =
    if (downloadDir.isEmpty)
      "$HOME/"
    else
      downloadDir

  val bootstrapJar =
    Option(Thread.currentThread().getContextClassLoader.getResourceAsStream("bootstrap.jar")) match {
      case Some(is) => Files.readFullySync(is)
      case None =>
        Console.err.println(s"Error: bootstrap JAR not found")
        sys.exit(1)
    }

  val helper = new Helper(common, remainingArgs)

  val artifacts = helper.res.artifacts

  val urls = artifacts.map(_.url)

  val unrecognized = urls.filter(s => !s.startsWith("http://") && !s.startsWith("https://"))
  if (unrecognized.nonEmpty)
    Console.err.println(s"Warning: non HTTP URLs:\n${unrecognized.mkString("\n")}")

  val output0 = new File(output)
  if (!force && output0.exists()) {
    Console.err.println(s"Error: $output already exists, use -f option to force erasing it.")
    sys.exit(1)
  }

  val shellPreamble = {
    Seq(
      "#!/usr/bin/env sh"
    ) ++
    env0.map { case (k, v) => "export " + k + "=\"" + v + "\"" } ++
    Seq(
      "exec java -jar \"$0\" " + (if (prependClasspath) "-B " else "") + "\"" + mainClass + "\" \"" + downloadDir + "\" " + urls.map("\"" + _ + "\"").mkString(" ") + " -- \"$@\"",
      ""
    )
  }.mkString("\n")

  try NIOFiles.write(output0.toPath, shellPreamble.getBytes("UTF-8") ++ bootstrapJar)
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
