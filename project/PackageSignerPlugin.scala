import sbt.*
import Keys.*
import sbt.internal.librarymanagement.IvyActions
import com.jsuereth.sbtpgp.SbtPgp
import com.typesafe.sbt.packager.universal.{ UniversalPlugin, UniversalDeployPlugin }
import com.typesafe.sbt.packager.debian.{ DebianPlugin, DebianDeployPlugin }
import com.typesafe.sbt.packager.rpm.{ RpmPlugin, RpmDeployPlugin }
import com.jsuereth.sbtpgp.gpgExtension

object PackageSignerPlugin extends sbt.AutoPlugin {
  override def trigger = allRequirements
  override def requires = SbtPgp && UniversalDeployPlugin && DebianDeployPlugin && RpmDeployPlugin

  import com.jsuereth.sbtpgp.PgpKeys.*
  import UniversalPlugin.autoImport.*
  import DebianPlugin.autoImport.*
  import RpmPlugin.autoImport.*

  override def projectSettings: Seq[Setting[?]] =
    inConfig(Universal)(packageSignerSettings) ++
      inConfig(Debian)(packageSignerSettings) ++
      inConfig(Rpm)(packageSignerSettings)

  def subExtension(art: Artifact, ext: String): Artifact =
    art.withExtension(ext)

  def packageSignerSettings: Seq[Setting[?]] = Seq(
    signedArtifacts := {
      val artifacts = packagedArtifacts.value
      val r = pgpSigner.value
      val skipZ = (pgpSigner / skip).value
      val s = streams.value
      if (!skipZ) {
        artifacts flatMap { case (art, f) =>
          Seq(
            art -> f,
            subExtension(art, art.extension + gpgExtension) ->
              r.sign(f, file(f.getAbsolutePath + gpgExtension), s)
          )
        }
      } else artifacts
    },
    publishSignedConfiguration := Classpaths.publishConfig(
      publishMavenStyle = publishMavenStyle.value,
      deliverIvyPattern =
        (Compile / packageBin / artifactPath).value.getParent + "/[artifact]-[revision](-[classifier]).[ext]",
      status = if (isSnapshot.value) "integration" else "release",
      configurations = Vector.empty,
      artifacts = signedArtifacts.value.toVector,
      checksums = (publish / checksums).value.toVector,
      resolverName = Classpaths.getPublishTo(publishTo.value).name,
      logging = ivyLoggingLevel.value,
      overwrite = isSnapshot.value
    ),
    publishLocalSignedConfiguration := Classpaths.publishConfig(
      publishMavenStyle = publishMavenStyle.value,
      deliverIvyPattern =
        (Compile / packageBin / artifactPath).value.getParent + "/[artifact]-[revision](-[classifier]).[ext]",
      status = if (isSnapshot.value) "integration" else "release",
      configurations = Vector.empty,
      artifacts = signedArtifacts.value.toVector,
      checksums = (publish / checksums).value.toVector,
      resolverName = "local",
      logging = ivyLoggingLevel.value,
      overwrite = isSnapshot.value
    ),
    publishSigned := Def.taskDyn {
      val config = publishSignedConfiguration.value
      val s = streams.value
      Def.task {
        IvyActions.publish(ivyModule.value, config, s.log)
      }
    }.value,
    publishLocalSigned := Def.taskDyn {
      val config = publishLocalSignedConfiguration.value
      val s = streams.value
      Def.task {
        IvyActions.publish(ivyModule.value, config, s.log)
      }
    }.value
  )

}
