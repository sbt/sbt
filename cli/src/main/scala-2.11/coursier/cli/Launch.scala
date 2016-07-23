package coursier
package cli

import java.net.{ URL, URLClassLoader }

import caseapp._

import scala.annotation.tailrec
import scala.language.reflectiveCalls
import scala.util.Try

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

class IsolatedClassLoader(
  urls: Array[URL],
  parent: ClassLoader,
  isolationTargets: Array[String]
) extends URLClassLoader(urls, parent) {

  /**
    * Applications wanting to access an isolated `ClassLoader` should inspect the hierarchy of
    * loaders, and look into each of them for this method, by reflection. Then they should
    * call it (still by reflection), and look for an agreed in advance target in it. If it is found,
    * then the corresponding `ClassLoader` is the one with isolated dependencies.
    */
  def getIsolationTargets: Array[String] = isolationTargets

}

// should be in case-app somehow
trait ExtraArgsApp extends caseapp.core.DefaultArgsApp {
  private var remainingArgs1 = Seq.empty[String]
  private var extraArgs1 = Seq.empty[String]

  override def setRemainingArgs(remainingArgs: Seq[String], extraArgs: Seq[String]): Unit = {
    remainingArgs1 = remainingArgs
    extraArgs1 = extraArgs
  }

  override def remainingArgs: Seq[String] =
    remainingArgs1
  def extraArgs: Seq[String] =
    extraArgs1
}

case class Launch(
  @Recurse
    options: LaunchOptions
) extends App with ExtraArgsApp {

  val userArgs = extraArgs

  val helper = new Helper(
    options.common,
    remainingArgs ++ options.isolated.rawIsolated.map { case (_, dep) => dep }
  )


  val files0 = helper.fetch(sources = false, javadoc = false)

  val contextLoader = Thread.currentThread().getContextClassLoader

  val parentLoader0: ClassLoader =
    Launch.mainClassLoader(contextLoader)
      .flatMap(cl => Option(cl.getParent))
      .getOrElse {
      // proguarded -> no risk of conflicts, no absolute need to find a specific ClassLoader
        val isProguarded = Try(contextLoader.loadClass("coursier.cli.Launch")).isFailure
        if (!isProguarded && options.common.verbosityLevel >= 0)
          Console.err.println(
            "Warning: cannot find the main ClassLoader that launched coursier.\n" +
            "Was coursier launched by its main launcher? " +
            "The ClassLoader of the application that is about to be launched will be intertwined " +
            "with the one of coursier, which may be a problem if their dependencies conflict."
          )
        contextLoader
      }

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

          if (options.common.verbosityLevel >= 2) {
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

      if (options.common.verbosityLevel >= 2) {
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

      if (options.common.verbosityLevel >= 2) {
        Console.err.println("Found main classes:")
        for (((vendor, title), mainClass) <- mainClasses)
          Console.err.println(s"  $mainClass (vendor: $vendor, title: $title)")
        Console.err.println("")
      }

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
  method.setAccessible(true)

  if (options.common.verbosityLevel >= 2)
    Helper.errPrintln(s"Launching $mainClass0 ${userArgs.mkString(" ")}")
  else if (options.common.verbosityLevel == 1)
    Helper.errPrintln(s"Launching")

  Thread.currentThread().setContextClassLoader(loader)
  method.invoke(null, userArgs.toArray)
}