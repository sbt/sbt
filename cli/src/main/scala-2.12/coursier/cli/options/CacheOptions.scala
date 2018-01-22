package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, _}
import coursier.Cache

final case class CacheOptions(
  @Help("Cache directory (defaults to environment variable COURSIER_CACHE or ~/.coursier/cache/v1)")
  @Short("C")
    cache: String = Cache.default.toString
)

object CacheOptions {
  implicit val parser = Parser[CacheOptions]
  implicit val help = caseapp.core.help.Help[CacheOptions]
}
