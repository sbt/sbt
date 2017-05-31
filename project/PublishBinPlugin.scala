import sbt._, Keys._

/** This local plugin provides ways of publishing just the binary jar. */
object PublishBinPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger  = allRequirements

  object autoImport {
    val publishLocalBin       = taskKey[Unit]("")
    val publishLocalBinConfig = taskKey[PublishConfiguration]("")
  }

  import autoImport._

  override def projectSettings = Def settings (
    publishLocalBin       := Classpaths.publishTask(publishLocalBinConfig, deliverLocal).value,
    publishLocalBinConfig := Classpaths.publishConfig(
      (packagedArtifacts in publishLocalBin).value, Some(deliverLocal.value),
      (checksums in publishLocalBin).value, logging = ivyLoggingLevel.value, overwrite = isSnapshot.value),
    packagedArtifacts in publishLocalBin := Classpaths.packaged(Seq(packageBin in Compile)).value
  )
}
