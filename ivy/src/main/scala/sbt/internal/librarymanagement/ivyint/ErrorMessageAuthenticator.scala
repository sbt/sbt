package sbt.internal.librarymanagement
package ivyint

import java.lang.reflect.InvocationTargetException
import java.net.{ Authenticator, PasswordAuthentication }

import org.apache.ivy.util.Message
import org.apache.ivy.util.url.IvyAuthenticator

/**
 * Helper to install an Authenticator that works with the IvyAuthenticator to provide better error messages when
 * credentials don't line up.
 */
object ErrorMessageAuthenticator {
  private var securityWarningLogged = false

  private def originalAuthenticator: Option[Authenticator] = {
    if (LMSysProp.isJavaVersion9Plus) getDefaultAuthenticator
    else getTheAuthenticator
  }

  private[this] def getTheAuthenticator: Option[Authenticator] = {
    withJavaReflectErrorHandling {
      val field = classOf[Authenticator].getDeclaredField("theAuthenticator")
      field.setAccessible(true)
      Option(field.get(null).asInstanceOf[Authenticator])
    }
  }

  private[this] def getDefaultAuthenticator: Option[Authenticator] =
    withJavaReflectErrorHandling {
      val method = classOf[Authenticator].getDeclaredMethod("getDefault")
      Option(method.invoke(null).asInstanceOf[Authenticator])
    }

  private[this] def withJavaReflectErrorHandling[A](t: => Option[A]): Option[A] = {
    try t
    catch {
      case e: ReflectiveOperationException => handleReflectionException(e)
      case e: SecurityException            => handleReflectionException(e)
      case e: InvocationTargetException    => handleReflectionException(e)
      case e: ExceptionInInitializerError  => handleReflectionException(e)
      case e: IllegalArgumentException     => handleReflectionException(e)
      case e: NullPointerException         => handleReflectionException(e)
      case e: ClassCastException           => handleReflectionException(e)
    }
  }

  private[this] def handleReflectionException(t: Throwable) = {
    Message.debug("Error occurred while getting the original authenticator: " + t.getMessage)
    None
  }

  private lazy val ivyOriginalField = {
    val field = classOf[IvyAuthenticator].getDeclaredField("original")
    field.setAccessible(true)
    field
  }
  // Attempts to get the original authenticator form the ivy class or returns null.
  private def installIntoIvy(ivy: IvyAuthenticator): Option[Authenticator] = {
    // Here we install ourselves as the IvyAuthenticator's default so we get called AFTER Ivy has a chance to run.
    def installIntoIvyImpl(original: Option[Authenticator]): Unit = {
      val newOriginal = new ErrorMessageAuthenticator(original)
      ivyOriginalField.set(ivy, newOriginal)
    }

    try
      Option(ivyOriginalField.get(ivy).asInstanceOf[Authenticator]) match {
        case Some(
              _: ErrorMessageAuthenticator
            ) => // We're already installed, no need to do the work again.
        case originalOpt => installIntoIvyImpl(originalOpt)
      }
    catch {
      case t: Throwable =>
        Message.debug(
          "Error occurred while trying to install debug messages into Ivy Authentication" + t.getMessage
        )
    }
    Some(ivy)
  }

  /** Installs the error message authenticator so we have nicer error messages when using java's URL for downloading. */
  def install(): Unit = {
    // Actually installs the error message authenticator.
    def doInstall(original: Option[Authenticator]): Unit =
      try Authenticator.setDefault(new ErrorMessageAuthenticator(original))
      catch {
        case _: SecurityException if !securityWarningLogged =>
          securityWarningLogged = true
          Message.warn(
            "Not enough permissions to set the ErrorMessageAuthenticator. "
              + "Helpful debug messages disabled!"
          );
      }
    // We will try to use the original authenticator as backup authenticator.
    // Since there is no getter available, so try to use some reflection to
    // obtain it. If that doesn't work, assume there is no original authenticator
    def doInstallIfIvy(original: Option[Authenticator]): Unit =
      original match {
        case Some(_: ErrorMessageAuthenticator) => // Ignore, we're already installed
        case Some(ivy: IvyAuthenticator) =>
          installIntoIvy(ivy); ()
        case original => doInstall(original)
      }
    doInstallIfIvy(originalAuthenticator)
  }
}

/**
 * An authenticator which just delegates to a previous authenticator and issues *nice*
 * error messages on failure to find credentials.
 *
 * Since ivy installs its own credentials handler EVERY TIME it resolves or publishes, we want to
 * install this one at some point and eventually ivy will capture it and use it.
 */
private[sbt] final class ErrorMessageAuthenticator(original: Option[Authenticator])
    extends Authenticator {

  protected override def getPasswordAuthentication(): PasswordAuthentication = {
    // We're guaranteed to only get here if Ivy's authentication fails
    if (!isProxyAuthentication) {
      val host = getRequestingHost
      // TODO - levenshtein distance "did you mean" message.
      Message.error(s"Unable to find credentials for [${getRequestingPrompt} @ ${host}].")
      val configuredRealms = IvyCredentialsLookup.realmsForHost.getOrElse(host, Set.empty)
      if (configuredRealms.nonEmpty) {
        Message.error(s"  Is one of these realms misspelled for host [${host}]:")
        configuredRealms foreach { realm =>
          Message.error(s"  * ${realm}")
        }
      }
    }
    // TODO - Maybe we should work on a helpful proxy message...

    // TODO - To be more maven friendly, we may want to also try to grab the "first" authentication that shows up for a server and try it.
    //        or maybe allow that behavior to be configured, since maven users aren't used to realms (which they should be).

    // Grabs the authentication that would have been provided had we not been installed...
    def originalAuthentication: Option[PasswordAuthentication] = {
      Authenticator.setDefault(original.orNull)
      try
        Option(
          Authenticator.requestPasswordAuthentication(
            getRequestingHost,
            getRequestingSite,
            getRequestingPort,
            getRequestingProtocol,
            getRequestingPrompt,
            getRequestingScheme
          )
        )
      finally Authenticator.setDefault(this)
    }
    originalAuthentication.orNull
  }

  /**
   * Returns true if this authentication if for a proxy and not for an HTTP server.
   *  We want to display different error messages, depending.
   */
  private def isProxyAuthentication: Boolean =
    getRequestorType == Authenticator.RequestorType.PROXY

}
