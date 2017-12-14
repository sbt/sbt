
import sbt._
import sbt.Keys._

import com.typesafe.tools.mima.plugin.MimaKeys._

import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Mima {

  // Important: the line with the "binary compatibility versions" comment below is matched during releases
  def binaryCompatibilityVersions = Set(
    "1.0.0-RC1",
    "1.0.0-RC2",
    "1.0.0-RC3",
    "1.0.0-RC4",
    "1.0.0-RC5",
    "1.0.0-RC6",
    "1.0.0-RC7",
    "1.0.0-RC8",
    "1.0.0-RC9",
    "1.0.0-RC10",
    "1.0.0-RC11",
    "1.0.0-RC12",
    "1.0.0-RC12-1",
    "1.0.0-RC13",
    "1.0.0-RC14",
    "1.0.0",
    "" // binary compatibility versions
  )


  lazy val previousArtifacts = Seq(
    mimaPreviousArtifacts := {
      binaryCompatibilityVersions.collect {
        case ver if ver.nonEmpty =>
          organization.value %%% moduleName.value % ver
      }
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
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.PropertiesPattern.parser"),
        // corresponds to a default value of a private method, not sure why this error is raised
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.maven.Pom.coursier$maven$Pom$$module$default$2")
      )
    }
  }

  lazy val cacheFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // these are private, don't know why they end-up appearing here
        // (probably related to https://github.com/typesafehub/migration-manager/issues/34)
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay#DownloadInfo")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay$DownloadInfo")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay#CheckUpdateInfo")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("coursier.TermDisplay#Info"))
      )
    }
  }

}
