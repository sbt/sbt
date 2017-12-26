package coursier
package cli

import java.io.File

import caseapp._

import scala.language.reflectiveCalls

case class Fetch(
  @Recurse
    options: FetchOptions
) extends App {

  val helper = new Helper(options.common, remainingArgs, ignoreErrors = options.artifactOptions.force)

  val files0 = helper.fetch(
    sources = options.sources,
    javadoc = options.javadoc,
    artifactTypes = options.artifactOptions.artifactTypes(
      options.sources || options.common.classifier0("sources"),
      options.javadoc || options.common.classifier0("javadoc")
    )
  )

  // Some progress lines seem to be scraped without this.
  Console.out.flush()

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
