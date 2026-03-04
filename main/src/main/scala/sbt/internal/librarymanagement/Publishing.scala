/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package librarymanagement

import java.nio.file.Path
import sbt.internal.util.MessageOnlyException
import sbt.io.IO
import sbt.io.Path.contentOf
import sbt.librarymanagement.Credentials
import sbt.librarymanagement.CredentialUtils
import sona.{ PublishingType, Sona }

import scala.concurrent.duration.FiniteDuration

object Publishing {
  val sonaRelease: Command =
    Command.command("sonaRelease")(sonatypeReleaseAction(PublishingType.Automatic))

  val sonaUpload: Command =
    Command.command("sonaUpload")(sonatypeReleaseAction(PublishingType.UserManaged))

  def makeBundle(stagingDir: Path, bundlePath: Path): Path = {
    if (bundlePath.toFile().exists()) {
      IO.delete(bundlePath.toFile())
    }
    IO.zip(
      sources = contentOf(stagingDir.toFile()),
      outputZip = bundlePath.toFile(),
      time = Some(0L),
    )
    bundlePath
  }

  private def sonatypeReleaseAction(publishingType: PublishingType)(s0: State): State = {
    import sbt.ProjectExtra.extract
    val extracted = Project.extract(s0)
    val log = extracted.get(Keys.sLog)
    val version = extracted.get(Keys.version)
    if (version.endsWith("-SNAPSHOT")) {
      log.error("""SNAPSHOTs are not supported on the Central Portal;
configure ThisBuild / publishTo to publish directly to the central-snapshots.
see https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for details.""")
      s0.fail
    } else {
      val deploymentName = extracted.get(Keys.sonaDeploymentName)
      val uploadRequestTimeout = extracted.get(Keys.sonaUploadRequestTimeout)
      val (s1, bundle) = extracted.runTask(Keys.sonaBundle, s0)
      val (s2, creds) = extracted.runTask(Keys.credentials, s1)
      val client = fromCreds(creds, uploadRequestTimeout)
      try {
        client.uploadBundle(bundle.toPath(), deploymentName, publishingType, log)
        s2
      } finally {
        client.close()
      }
    }
  }

  private def fromCreds(creds: Seq[Credentials], uploadRequestTimeout: FiniteDuration): Sona = {
    val cred = CredentialUtils
      .forHost(creds, Sona.host)
      .getOrElse(throw new MessageOnlyException(s"no credentials are found for ${Sona.host}"))
    Sona.oauthClient(cred.userName, cred.passwd, uploadRequestTimeout)
  }
}
