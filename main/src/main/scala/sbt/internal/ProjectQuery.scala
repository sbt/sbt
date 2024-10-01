package sbt
package internal

import sbt.internal.util.complete.{ DefaultParsers, Parser }
import sbt.Keys.scalaBinaryVersion
import DefaultParsers.*
import scala.annotation.nowarn
import scala.util.matching.Regex
import sbt.internal.util.AttributeKey

private[sbt] case class ProjectQuery(
    projectName: String,
    params: Map[AttributeKey[?], String],
):
  import ProjectQuery.*
  private lazy val pattern: Regex = Regex("^" + projectName.replace(wildcard, ".*") + "$")

  @nowarn
  def buildQuery(structure: BuildStructure): ProjectRef => Boolean =
    (p: ProjectRef) =>
      val projectMatches =
        if projectName == wildcard then true
        else pattern.matches(p.project)
      val scalaMatches =
        params.get(Keys.scalaBinaryVersion.key) match
          case Some(expected) =>
            val actualSbv = structure.data.get(Scope.ThisScope.rescope(p), scalaBinaryVersion.key)
            actualSbv match
              case Some(sbv) => sbv == expected
              case None      => true
          case None => true
      projectMatches && scalaMatches
end ProjectQuery

object ProjectQuery:
  private val wildcard = "..."

  // make sure @ doesn't match on this one
  def projectName: Parser[String] =
    charClass(c => c.isLetter || c.isDigit || c == '_' || c == '.').+.string
      .examples(wildcard)

  def parser: Parser[ProjectQuery] =
    (projectName ~
      token("@scalaBinaryVersion=" ~> StringBasic.map((scalaBinaryVersion.key, _)))
        .examples("@scalaBinaryVersion=3")
        .?)
      .map { case (proj, params) =>
        ProjectQuery(proj, Map(params.toSeq: _*))
      }
      .filter(
        (q) => q.projectName.contains("...") || q.params.nonEmpty,
        (msg) => s"$msg isn't a query"
      )
end ProjectQuery
