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
    remainingArgs ++ options.isolated.rawIsolated.map { case (_, dep) => dep },
    isolated = options.isolated
  )

  val mainClass =
    if (options.mainClass.isEmpty)
      helper.retainedMainClass
    else
      options.mainClass

  val cls =
    try helper.loader.loadClass(mainClass)
    catch { case e: ClassNotFoundException =>
      Helper.errPrintln(s"Error: class $mainClass not found")
      sys.exit(255)
    }
  val method =
    try cls.getMethod("main", classOf[Array[String]])
    catch { case e: NoSuchMethodException =>
      Helper.errPrintln(s"Error: method main not found in $mainClass")
      sys.exit(255)
    }
  method.setAccessible(true)

  if (options.common.verbosityLevel >= 2)
    Helper.errPrintln(s"Launching $mainClass ${userArgs.mkString(" ")}")
  else if (options.common.verbosityLevel == 1)
    Helper.errPrintln(s"Launching")

  Thread.currentThread().setContextClassLoader(helper.loader)
  try method.invoke(null, userArgs.toArray)
  catch {
    case e: java.lang.reflect.InvocationTargetException =>
      throw Option(e.getCause).getOrElse(e)
  }
}