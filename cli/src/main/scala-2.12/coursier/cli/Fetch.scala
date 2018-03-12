package coursier
package cli

import java.io.File

import caseapp._
import coursier.cli.options.FetchOptions

final class Fetch(options: FetchOptions, args: RemainingArgs) {

  val helper = new Helper(options.common, args.all, ignoreErrors = options.artifactOptions.force)

  val files0 = helper.fetch(
    sources = options.sources,
    javadoc = options.javadoc,
    artifactTypes = options.artifactOptions.artifactTypes(
      options.sources || options.common.classifier0("sources"),
      options.javadoc || options.common.classifier0("javadoc")
    )
  )

}

object Fetch extends CaseApp[FetchOptions] {

  def apply(options: FetchOptions, args: RemainingArgs): Fetch =
    new Fetch(options, args)

  def run(options: FetchOptions, args: RemainingArgs): Unit = {

    val fetch = Fetch(options, args)

    // Some progress lines seem to be scraped without this.
    Console.out.flush()

    val out =
      if (options.classpath)
        fetch
          .files0
          .map(_.toString)
          .mkString(File.pathSeparator)
      else
        fetch
          .files0
          .map(_.toString)
          .mkString("\n")

    println(out)
  }

}
