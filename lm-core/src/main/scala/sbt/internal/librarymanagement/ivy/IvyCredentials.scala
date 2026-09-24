package sbt.internal.librarymanagement
package ivy

import java.io.File
import sbt.librarymanagement.{ Credentials, CredentialUtils }

// Kept for backward compatibility with sbt2-compat
private[sbt] object IvyCredentials:
  def forHost(sc: Seq[Credentials], host: String) =
    CredentialUtils.forHost(sc, host)
  def allDirect(sc: Seq[Credentials]): Seq[Credentials.DirectCredentials] =
    CredentialUtils.allDirect(sc)
  def toDirect(c: Credentials): Credentials.DirectCredentials =
    CredentialUtils.toDirect(c)
  def loadCredentials(path: File): Either[String, Credentials.DirectCredentials] =
    CredentialUtils.loadCredentials(path)
end IvyCredentials
