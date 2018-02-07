package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.{Attributes, Dependency}
import coursier.util.Parse


final case class IsolatedLoaderOptions(
  @Value("target:dependency")
  @Short("I")
    isolated: List[String] = Nil,
  @Help("Comma-separated isolation targets")
  @Short("i")
    isolateTarget: List[String] = Nil
) {

  def anyIsolatedDep = isolateTarget.nonEmpty || isolated.nonEmpty

  lazy val targets = {
    val l = isolateTarget.flatMap(_.split(',')).filter(_.nonEmpty)
    val (invalid, valid) = l.partition(_.contains(":"))
    if (invalid.nonEmpty) {
      Console.err.println(s"Invalid target IDs:")
      for (t <- invalid)
        Console.err.println(s"  $t")
      sys.exit(255)
    }
    if (valid.isEmpty)
      Array("default")
    else
      valid.toArray
  }

  lazy val (validIsolated, unrecognizedIsolated) = isolated.partition(s => targets.exists(t => s.startsWith(t + ":")))

  def check() = {
    if (unrecognizedIsolated.nonEmpty) {
      Console.err.println(s"Unrecognized isolation targets in:")
      for (i <- unrecognizedIsolated)
        Console.err.println(s"  $i")
      sys.exit(255)
    }
  }

  lazy val rawIsolated = validIsolated.map { s =>
    val Array(target, dep) = s.split(":", 2)
    target -> dep
  }

  def isolatedModuleVersions(defaultScalaVersion: String) = rawIsolated.groupBy { case (t, _) => t }.map {
    case (t, l) =>
      val (errors, modVers) = Parse.moduleVersions(l.map { case (_, d) => d }, defaultScalaVersion)

      if (errors.nonEmpty) {
        errors.foreach(Console.err.println)
        sys.exit(255)
      }

      t -> modVers
  }

  def isolatedDeps(defaultScalaVersion: String) =
    isolatedModuleVersions(defaultScalaVersion).map {
      case (t, l) =>
        t -> l.map {
          case (mod, ver) =>
            Dependency(
              mod,
              ver,
              configuration = "runtime",
              attributes = Attributes("", "")
            )
        }
    }

}

object IsolatedLoaderOptions {
  implicit val parser = Parser[IsolatedLoaderOptions]
  implicit val help = caseapp.core.help.Help[IsolatedLoaderOptions]
}
