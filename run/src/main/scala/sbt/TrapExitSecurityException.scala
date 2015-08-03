package sbt

/**
 * A custom SecurityException that tries not to be caught.  Closely based on a similar class in Nailgun.
 * The main goal of this exception is that once thrown, it propagates all of the way up the call stack,
 * terminating the thread's execution.
 */
private final class TrapExitSecurityException(val exitCode: Int) extends SecurityException {
  private var accessAllowed = false
  def allowAccess(): Unit = {
    accessAllowed = true
  }
  override def printStackTrace = ifAccessAllowed(super.printStackTrace)
  override def toString = ifAccessAllowed(super.toString)
  override def getCause = ifAccessAllowed(super.getCause)
  override def getMessage = ifAccessAllowed(super.getMessage)
  override def fillInStackTrace = ifAccessAllowed(super.fillInStackTrace)
  override def getLocalizedMessage = ifAccessAllowed(super.getLocalizedMessage)
  private def ifAccessAllowed[T](f: => T): T =
    {
      if (accessAllowed)
        f
      else
        throw this
    }
}
