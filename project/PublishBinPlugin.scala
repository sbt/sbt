import sbt._, Keys._

import sbt.librarymanagement.PublishConfiguration
import sbt.librarymanagement.ConfigRef

/** This local plugin provides ways of publishing just the binary jar. */
object PublishBinPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val publishLocalBin = taskKey[Unit]("")
    val publishLocalBinConfig = taskKey[PublishConfiguration]("")
  }
  import autoImport._

  override def globalSettings = Seq(publishLocalBin := (()))

  override def projectSettings = Def settings (
    publishLocalBin := Classpaths.publishTask(publishLocalBinConfig, deliverLocal).value,
    publishLocalBinConfig := {
      Classpaths.publishConfig(
        false, // publishMavenStyle.value,
        Classpaths.deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        (packagedArtifacts in publishLocalBin).value.toVector,
        (checksums in publishLocalBin).value.toVector,
        logging = ivyLoggingLevel.value,
        overwrite = isSnapshot.value
      )
    },
    packagedArtifacts in publishLocalBin := Classpaths.packaged(Seq(packageBin in Compile)).value
  )
}
