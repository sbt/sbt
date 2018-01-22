package coursier.cli.options

import caseapp.{ HelpMessage => Help, ExtraName => Short, _ }

final case class LaunchOptions(
  @Short("M")
  @Short("main")
    mainClass: String = "",
  @Short("J")
  @Help("Extra JARs to be added to the classpath of the launched application. Directories accepted too.")
    extraJars: List[String] = Nil,
  @Recurse
    isolated: IsolatedLoaderOptions = IsolatedLoaderOptions(),
  @Recurse
    common: CommonOptions = CommonOptions()
)

object LaunchOptions {
  implicit val parser = Parser[LaunchOptions]
  implicit val help = caseapp.core.help.Help[LaunchOptions]
}
