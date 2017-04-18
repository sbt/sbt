
import sbt._
import sbt.Keys._

import com.typesafe.tools.mima.plugin.MimaKeys._

object Mima {

  def binaryCompatibilityVersion = "1.0.0-RC1"


  lazy val previousArtifacts = Seq(
    mimaPreviousArtifacts := {
      Set(organization.value %% moduleName.value % binaryCompatibilityVersion)
    }
  )

  lazy val coreFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // ignore shaded-stuff related errors
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.shaded.")),
        // was private, now removed
        ProblemFilters.exclude[MissingClassProblem]("coursier.ivy.PropertiesPattern$Parser$"),
        // made private so that the shaded fastparse stuff doesn't leak
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.PropertiesPattern.parser")
      )
    }
  }

  lazy val cacheFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq()
    }
  }

}
