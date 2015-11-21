package coursier
package cli

import java.io.File
import java.net.URLClassLoader

import caseapp._

case class CommonOptions(
  @HelpMessage("Keep optional dependencies (Maven)")
    keepOptional: Boolean,
  @HelpMessage("Off-line mode: only use cache and local repositories")
  @ExtraName("c")
    offline: Boolean,
  @HelpMessage("Force download: for remote repositories only: re-download items, that is, don't use cache directly")
  @ExtraName("f")
    force: Boolean,
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
  @HelpMessage("Maximum number of parallel downloads (default: 6)")
  @ExtraName("n")
    parallel: Int = 6
) {
  val verbose0 = verbose.length + (if (quiet) 1 else 0)
}

sealed trait Command extends App

case class Fetch(
  @HelpMessage("Fetch source artifacts")
  @ExtraName("S")
    sources: Boolean,
  @HelpMessage("Fetch javadoc artifacts")
  @ExtraName("D")
    javadoc: Boolean,
  @Recurse
    common: CommonOptions
) extends Command {

  val helper = new Helper(common, remainingArgs)

  val files0 = helper.fetch(main = true, sources = false, javadoc = false)

  Console.out.println(
    files0
      .map(_.toString)
      .mkString("\n")
  )

}

case class Launch(
  @HelpMessage("If -L or --launch is specified, the main class to launch")
  @ExtraName("M")
  @ExtraName("main")
    mainClass: String,
  @Recurse
    common: CommonOptions
) extends Command {

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


  def printParents(cl: ClassLoader): Unit =
    Option(cl.getParent) match {
      case None =>
      case Some(cl0) =>
        println(cl0.toString)
        printParents(cl0)
    }

  printParents(Thread.currentThread().getContextClassLoader)

  import scala.collection.JavaConverters._
  val cl = new URLClassLoader(
    files0.map(_.toURI.toURL).toArray,
    Thread.currentThread().getContextClassLoader // setting this to null provokes strange things (wrt terminal, ...)
  )

  val mainClass0 =
    if (mainClass.nonEmpty)
      mainClass
    else {
      val metaInfs = cl.findResources("META-INF/MANIFEST.MF").asScala.toVector
      val mainClasses = metaInfs.flatMap { url =>
        Option(new java.util.jar.Manifest(url.openStream()).getMainAttributes.getValue("Main-Class"))
      }

      if (mainClasses.isEmpty) {
        println(s"No main class found. Specify one with -M or --main.")
        sys.exit(255)
      }

      if (common.verbose0 >= 0)
        println(s"Found ${mainClasses.length} main class(es):\n${mainClasses.map("  " + _).mkString("\n")}")

      mainClasses.head
    }

  val cls =
    try cl.loadClass(mainClass0)
    catch { case e: ClassNotFoundException =>
      println(s"Error: class $mainClass0 not found")
      sys.exit(255)
    }
  val method =
    try cls.getMethod("main", classOf[Array[String]])
    catch { case e: NoSuchMethodError =>
      println(s"Error: method main not found in $mainClass0")
      sys.exit(255)
    }

  if (common.verbose0 >= 1)
    println(s"Calling $mainClass0 ${extraArgs.mkString(" ")}")

  Thread.currentThread().setContextClassLoader(cl)
  method.invoke(null, extraArgs.toArray)
}

case class Classpath(
  @Recurse
    common: CommonOptions
) extends Command {

  val helper = new Helper(common, remainingArgs)

  val files0 = helper.fetch(main = true, sources = false, javadoc = false)

  Console.out.println(
    files0
      .map(_.toString)
      .mkString(File.pathSeparator)
  )

}

// TODO: allow removing a repository (with confirmations, etc.)
case class Repository(
  @ValueDescription("id:baseUrl")
  @ExtraName("a")
    add: List[String],
  @ExtraName("L")
    list: Boolean,
  @ExtraName("l")
    defaultList: Boolean,
  ivyLike: Boolean
) extends Command {

  if (add.exists(!_.contains(":"))) {
    CaseApp.printUsage[Repository](err = true)
    sys.exit(255)
  }

  val add0 = add
    .map{ s =>
      val Seq(id, baseUrl) = s.split(":", 2).toSeq
      id -> baseUrl
    }

  if (
    add0.exists(_._1.contains("/")) ||
      add0.exists(_._1.startsWith(".")) ||
      add0.exists(_._1.isEmpty)
  ) {
    CaseApp.printUsage[Repository](err = true)
    sys.exit(255)
  }


  val cache = Cache.default

  if (cache.cache.exists() && !cache.cache.isDirectory) {
    Console.err.println(s"Error: ${cache.cache} not a directory")
    sys.exit(1)
  }

  if (!cache.cache.exists())
    cache.init(verbose = true)

  val current = cache.list().map(_._1).toSet

  val alreadyAdded = add0
    .map(_._1)
    .filter(current)

  if (alreadyAdded.nonEmpty) {
    Console.err.println(s"Error: already added: ${alreadyAdded.mkString(", ")}")
    sys.exit(1)
  }

  for ((id, baseUrl0) <- add0) {
    val baseUrl =
      if (baseUrl0.endsWith("/"))
        baseUrl0
      else
        baseUrl0 + "/"

    cache.add(id, baseUrl, ivyLike = ivyLike)
  }

  if (defaultList) {
    val map = cache.repositoryMap()

    for (id <- cache.default(withNotFound = true))
      map.get(id) match {
        case Some(repo) =>
          println(s"$id: ${repo.root}" + (if (repo.ivyLike) " (Ivy-like)" else ""))
        case None =>
          println(s"$id (not found)")
      }
  }

  if (list)
    for ((id, repo, _) <- cache.list().sortBy(_._1)) {
      println(s"$id: ${repo.root}" + (if (repo.ivyLike) " (Ivy-like)" else ""))
    }

}

object CoursierApp extends CommandAppOf[Command]
