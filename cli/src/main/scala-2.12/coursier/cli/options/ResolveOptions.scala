package coursier.cli.options

import caseapp._

final case class ResolveOptions(
  @Recurse
    common: CommonOptions = CommonOptions()
)
