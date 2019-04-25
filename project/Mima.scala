
import com.typesafe.tools.mima.plugin.MimaPlugin
import sbt._
import sbt.Keys._
import sys.process._

object Mima {

  private def stable(ver: String): Boolean =
    ver.exists(c => c != '0' && c != '.') &&
    ver
      .replace("-RC", "-")
      .forall(c => c == '.' || c == '-' || c.isDigit)

  def binaryCompatibilityVersions: Set[String] =
    Seq("git", "tag", "--merged", "HEAD^", "--contains", "736d5c11")
      .!!
      .linesIterator
      .map(_.trim)
      .filter(_.startsWith("v"))
      .map(_.stripPrefix("v"))
      .filter(stable)
      .toSet

  def settings: Seq[Setting[_]] = Seq(
    MimaPlugin.autoImport.mimaPreviousArtifacts := {
      binaryCompatibilityVersions.map { ver =>
        (organization.value % moduleName.value % ver).cross(crossVersion.value)
      }
    }
  )

}
