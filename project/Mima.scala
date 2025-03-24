import com.typesafe.tools.mima.plugin.MimaPlugin
import com.typesafe.tools.mima.plugin.MimaKeys.*
import sbt.*
import sbt.Keys.*
import sys.process.*

object Mima {

  private def stable(ver: String): Boolean =
    ver.exists(c => c != '0' && c != '.') &&
      ver
        .replace("-RC", "-")
        .forall(c => c == '.' || c == '-' || c.isDigit)

  def binaryCompatibilityVersions: Set[String] =
    Seq("git", "tag", "--merged", "HEAD^", "--contains", "v2.0.0-RC3-6").!!.linesIterator
      .map(_.trim)
      .filter(_.startsWith("v"))
      .map(_.stripPrefix("v"))
      .filter(stable)
      .toSet

  def settings: Seq[Setting[_]] = Seq(
    MimaPlugin.autoImport.mimaPreviousArtifacts := Set.empty,
    // MimaPlugin.autoImport.mimaPreviousArtifacts := {
    //   binaryCompatibilityVersions.map { ver =>
    //     (organization.value % moduleName.value % ver).cross(crossVersion.value)
    //   }
    // }
  )

  lazy val lmCoursierFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core.*

      Seq(
        // spurious errors on CI
        ProblemFilters.exclude[IncompatibleSignatureProblem]("*"),
        // Methods that shouldn't have been there
        ProblemFilters.exclude[DirectMissingMethodProblem](
          "lmcoursier.credentials.FileCredentials.get"
        ),
        ProblemFilters.exclude[DirectMissingMethodProblem](
          "lmcoursier.credentials.DirectCredentials.matches"
        ),
        ProblemFilters.exclude[DirectMissingMethodProblem](
          "lmcoursier.credentials.DirectCredentials.get"
        ),
        ProblemFilters.exclude[DirectMissingMethodProblem](
          "lmcoursier.credentials.DirectCredentials.autoMatches"
        ),
        ProblemFilters.exclude[DirectMissingMethodProblem](
          "lmcoursier.credentials.Credentials.get"
        ),
        // Removed unused method, shouldn't have been there in the first place
        ProblemFilters.exclude[DirectMissingMethodProblem](
          "lmcoursier.credentials.DirectCredentials.authentication"
        ),
        // ignore shaded and internal stuff related errors
        (pb: Problem) => pb.matchName.forall(!_.startsWith("lmcoursier.internal."))
      )
    }
  }

  lazy val lmCoursierShadedFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core.*

      Seq(
        // spurious errors on CI
        ProblemFilters.exclude[IncompatibleSignatureProblem]("*"),
        // Should have been put under lmcoursier.internal?
        (pb: Problem) => pb.matchName.forall(!_.startsWith("lmcoursier.definitions.ToCoursier.")),
        (pb: Problem) => pb.matchName.forall(!_.startsWith("lmcoursier.definitions.FromCoursier."))
      )
    }
  }

}
