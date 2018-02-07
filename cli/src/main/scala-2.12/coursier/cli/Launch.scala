package coursier
package cli

import java.io.File

import caseapp._
import coursier.cli.options.LaunchOptions

object Launch extends CaseApp[LaunchOptions] {

  def apply(
    loader: ClassLoader,
    mainClass: String,
    args: Seq[String],
    verbosity: Int,
    beforeMain: => Unit = ()
  ): Unit = {

    val cls =
      try loader.loadClass(mainClass)
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

    if (verbosity >= 2)
      Helper.errPrintln(s"Launching $mainClass ${args.mkString(" ")}")
    else if (verbosity == 1)
      Helper.errPrintln(s"Launching")

    beforeMain

    Thread.currentThread().setContextClassLoader(loader)
    try method.invoke(null, args.toArray)
    catch {
      case e: java.lang.reflect.InvocationTargetException =>
        throw Option(e.getCause).getOrElse(e)
    }
  }


  def run(options: LaunchOptions, args: RemainingArgs): Unit = {

    val userArgs = args.unparsed

    val helper = new Helper(
      options.common,
      args.remaining ++ options.isolated.rawIsolated.map { case (_, dep) => dep },
      extraJars = options.extraJars.map(new File(_)),
      isolated = options.isolated
    )

    val mainClass =
      if (options.mainClass.isEmpty)
        helper.retainedMainClass
      else
        options.mainClass

    Launch(
      helper.loader,
      mainClass,
      userArgs,
      options.common.verbosityLevel
    )
  }

}