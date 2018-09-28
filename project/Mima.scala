
import sbt._
import sbt.Keys._

import com.typesafe.tools.mima.plugin.MimaKeys._

object Mima {

  // Important: the line with the "binary compatibility versions" comment below is matched during releases
  def binaryCompatibilityVersions = Set(
    "" // binary compatibility versions
  )


  lazy val previousArtifacts = Seq(
    mimaPreviousArtifacts := {
      binaryCompatibilityVersions.collect {
        case ver if ver.nonEmpty =>
          organization.value %% moduleName.value % ver
      }
    }
  )

}
