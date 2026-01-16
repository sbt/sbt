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

  def settings: Seq[Setting[?]] = Seq(
    MimaPlugin.autoImport.mimaPreviousArtifacts := Set.empty,
    // MimaPlugin.autoImport.mimaPreviousArtifacts := {
    //   binaryCompatibilityVersions.map { ver =>
    //     (organization.value % moduleName.value % ver).cross(crossVersion.value)
    //   }
    // }
  )

  lazy val lmCoursierFilters = {
    mimaBinaryIssueFilters ++= Seq(
    )
  }

  lazy val lmCoursierShadedFilters = {
    mimaBinaryIssueFilters ++= Seq(
    )
  }

}
