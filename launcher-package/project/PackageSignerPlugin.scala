import sbt._
import Keys._
import com.jsuereth.sbtpgp.SbtPgp
import com.typesafe.sbt.packager.universal.{ UniversalPlugin, UniversalDeployPlugin }
import com.typesafe.sbt.packager.debian.{ DebianPlugin, DebianDeployPlugin }
import com.typesafe.sbt.packager.rpm.{ RpmPlugin, RpmDeployPlugin }
import com.jsuereth.sbtpgp.gpgExtension

object PackageSignerPlugin extends sbt.AutoPlugin {
  override def trigger = allRequirements
  override def requires = SbtPgp && UniversalDeployPlugin && DebianDeployPlugin && RpmDeployPlugin

  import com.jsuereth.sbtpgp.PgpKeys._
  import UniversalPlugin.autoImport._
  import DebianPlugin.autoImport._
  import RpmPlugin.autoImport._

  override def projectSettings: Seq[Setting[_]] =
    inConfig(Universal)(packageSignerSettings) ++
    inConfig(Debian)(packageSignerSettings) ++
    inConfig(Rpm)(packageSignerSettings)

  def subExtension(art: Artifact, ext: String): Artifact =
    art.copy(extension = ext)

  def packageSignerSettings: Seq[Setting[_]] = Seq(
    signedArtifacts := {
      val artifacts = packagedArtifacts.value
      val r = pgpSigner.value
      val skipZ = (skip in pgpSigner).value
      val s = streams.value
      if (!skipZ) {
        artifacts flatMap { case (art, f) =>
          Seq(art -> f,
              subExtension(art, art.extension + gpgExtension) ->
              r.sign(f, file(f.getAbsolutePath + gpgExtension), s))
        }
      }
      else artifacts
    },
    publishSignedConfiguration := Classpaths.publishConfig(
      signedArtifacts.value,
      None,
      resolverName = Classpaths.getPublishTo(publishTo.value).name,
      checksums    = (checksums in publish).value,
      logging      = ivyLoggingLevel.value,
      overwrite    = isSnapshot.value),
    publishLocalSignedConfiguration := Classpaths.publishConfig(
      signedArtifacts.value,
      None,
      resolverName = "local",
      checksums    = (checksums in publish).value,
      logging      = ivyLoggingLevel.value,
      overwrite    = isSnapshot.value),
    publishSigned      := Classpaths.publishTask(publishSignedConfiguration, deliver).value,
    publishLocalSigned := Classpaths.publishTask(publishLocalSignedConfiguration, deliver).value
  )

}

