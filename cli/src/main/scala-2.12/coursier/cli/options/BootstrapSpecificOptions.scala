package coursier.cli.options

import caseapp.{ HelpMessage => Help, ValueDescription => Value, ExtraName => Short, _ }

final case class BootstrapSpecificOptions(
  @Short("M")
  @Short("main")
    mainClass: String = "",
  @Short("o")
    output: String = "bootstrap",
  @Short("f")
    force: Boolean = false,
  @Help("Generate a standalone launcher, with all JARs included, instead of one downloading its dependencies on startup.")
  @Short("s")
    standalone: Boolean = false,
  @Help("Set Java properties in the generated launcher.")
  @Value("key=value")
  @Short("D")
    property: List[String] = Nil,
  @Help("Set Java command-line options in the generated launcher.")
  @Value("option")
  @Short("J")
    javaOpt: List[String] = Nil,
  @Help("Generate native launcher")
  @Short("S")
    native: Boolean = false,
  @Help("Native compilation target directory")
  @Short("d")
    target: String = "native-target",
  @Help("Don't wipe native compilation target directory (for debug purposes)")
    keepTarget: Boolean = false,
  @Help("Generate an assembly rather than a bootstrap jar")
  @Short("a")
    assembly: Boolean = false,
  @Help("Add assembly rule")
  @Value("append:$path|append-pattern:$pattern|exclude:$path|exclude-pattern:$pattern")
  @Short("R")
    rule: List[String] = Nil,
  @Help("Add default rules to assembly rule list")
    defaultRules: Boolean = true,
  @Help("Add preamble")
    preamble: Boolean = true,
  @Recurse
    isolated: IsolatedLoaderOptions = IsolatedLoaderOptions(),
  @Recurse
    common: CommonOptions = CommonOptions()
)

object BootstrapSpecificOptions {
  implicit val parser = Parser[BootstrapSpecificOptions]
  implicit val help = caseapp.core.help.Help[BootstrapSpecificOptions]
}
