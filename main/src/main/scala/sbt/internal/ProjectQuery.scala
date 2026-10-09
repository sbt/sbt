package sbt
package internal

import sbt.internal.util.complete.{ DefaultParsers, Parser }
import DefaultParsers.*
import sbt.librarymanagement.Platform
import scala.util.matching.Regex
import sbt.internal.util.AttributeKey

private[sbt] case class ProjectQuery(
    projectName: String,
    params: Map[AttributeKey[?], String],
):
  import ProjectQuery.*
  private lazy val pattern: Regex = Regex("^" + projectName.replace(wildcard, ".*") + "$")

  def buildQuery(structure: BuildStructure): ProjectRef => Boolean =
    (p: ProjectRef) =>
      val projectMatches =
        if projectName == wildcard then true
        else pattern.matches(p.project)
      projectMatches && params.forall: (key, expected) =>
        structure.data.get(Def.ScopedKey(Scope.ThisScope.rescope(p), key)) match
          case Some(actual) => actual == expected
          case None         => true
end ProjectQuery

object ProjectQuery:
  private val wildcard = "..."

  private[sbt] val queryKeys: Seq[(AttributeKey[?], Set[String])] =
    Seq(
      Keys.scalaBinaryVersion.key -> Set("3", "2.13", "2.12"),
      Keys.platform.key -> Set(Platform.jvm, Platform.sjs1, Platform.native0_5),
    )

  // make sure @ doesn't match on this one
  def projectName: Parser[String] =
    charClass(c => c.isLetter || c.isDigit || c == '_' || c == '.').+.string
      .examples(wildcard)

  private def paramValue: Parser[String] =
    charClass(c => c.isLetterOrDigit || c == '_' || c == '.' || c == '-').+.string

  private def param: Parser[(AttributeKey[?], String)] =
    queryKeys
      .map: (key, examples) =>
        token("@" + key.label + "=") ~>
          token(paramValue.examples(examples)).map(v => (key: AttributeKey[?], v))
      .reduce(_ | _)

  def parser: Parser[ProjectQuery] =
    (projectName ~ param.*)
      .map { (proj, params) =>
        ProjectQuery(proj, params.toMap)
      }
      .filter(
        (q) => q.projectName.contains("...") || q.params.nonEmpty,
        (msg) => s"$msg isn't a query"
      )
end ProjectQuery
