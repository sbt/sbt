import org.scalafmt.bootstrap.ScalafmtBootstrap
import org.scalafmt.sbt.ScalafmtPlugin
import sbt._
import sbt.Keys._
import sbt.inc.Analysis

// Taken from https://github.com/akka/alpakka/blob/master/project/AutomateScalafmtPlugin.scala
object AutomateScalafmtPlugin extends AutoPlugin {

  object autoImport {
    def automateScalafmtFor(configurations: Configuration*): Seq[Setting[_]] =
      configurations.flatMap { c =>
        inConfig(c)(
          Seq(
            compileInputs.in(compile) := {
              scalafmtInc.value
              compileInputs.in(compile).value
            },
            sourceDirectories.in(scalafmtInc) := Seq(scalaSource.value),
            scalafmtInc := {
              val cache = streams.value.cacheDirectory / "scalafmt"
              val include = includeFilter.in(scalafmtInc).value
              val exclude = excludeFilter.in(scalafmtInc).value
              val sources =
                sourceDirectories
                  .in(scalafmtInc)
                  .value
                  .descendantsExcept(include, exclude)
                  .get
                  .toSet
              def format(handler: Set[File] => Unit, msg: String) = {
                def update(handler: Set[File] => Unit, msg: String)(in: ChangeReport[File],
                                                                    out: ChangeReport[File]) = {
                  val label = Reference.display(thisProjectRef.value)
                  val files = in.modified -- in.removed
                  Analysis
                    .counted("Scala source", "", "s", files.size)
                    .foreach(count => streams.value.log.info(s"$msg $count in $label ..."))
                  handler(files)
                  files
                }
                FileFunction.cached(cache)(FilesInfo.hash, FilesInfo.exists)(update(handler, msg))(
                  sources
                )
              }
              def formattingHandler(files: Set[File]) =
                if (files.nonEmpty) {
                  val filesArg = files.map(_.getAbsolutePath).mkString(",")
                  ScalafmtBootstrap.main(List("--quiet", "-i", "-f", filesArg))
                }
              format(formattingHandler, "Formatting")
              format(_ => (), "Reformatted") // Recalculate the cache
            }
          )
        )
      }
  }

  private val scalafmtInc = taskKey[Unit]("Incrementally format modified sources")

  override def requires = ScalafmtPlugin

  override def trigger = allRequirements

  override def projectSettings =
    (includeFilter.in(scalafmtInc) := "*.scala") +: autoImport.automateScalafmtFor(Compile, Test)
}
