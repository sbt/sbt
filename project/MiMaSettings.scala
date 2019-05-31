package sbtbuild

import sbt._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._

object MimaSettings {
  val mimaSettings = Def.settings (
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("sbt.TupleSyntax.t*ToApp*"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("sbt.TupleSyntax.t*ToTable*"),
      ProblemFilters.exclude[MissingClassProblem]("sbt.Scoped$Apply*"),
      ProblemFilters.exclude[MissingClassProblem]("sbt.Scoped$RichTaskable*"),
      ProblemFilters.exclude[MissingClassProblem]("sbt.Scoped$RichTaskables"),
      ProblemFilters.exclude[MissingClassProblem]("sbt.internal.util.AList$T*K"),
    ),
  )
}
