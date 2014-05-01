package xsbt.boot

import Pre._

object JAnsi {
  def uninstall(loader: ClassLoader): Unit = callJAnsi("systemUninstall", loader)
  def install(loader: ClassLoader): Unit = callJAnsi("systemInstall", loader)

  private[this] def callJAnsi(methodName: String, loader: ClassLoader): Unit = if (isWindows && !isCygwin) callJAnsiMethod(methodName, loader)
  private[this] def callJAnsiMethod(methodName: String, loader: ClassLoader): Unit =
    try {
      val c = Class.forName("org.fusesource.jansi.AnsiConsole", true, loader)
      c.getMethod(methodName).invoke(null)
    } catch {
      case ignore: ClassNotFoundException =>
      /* The below code intentionally traps everything. It technically shouldn't trap the
				* non-StackOverflowError VirtualMachineErrors and AWTError would be weird, but this is PermGen
				* mitigation code that should not render sbt completely unusable if jansi initialization fails.
				* [From Mark Harrah, https://github.com/sbt/sbt/pull/633#issuecomment-11957578].
				*/
      case ex: Throwable                  => println("Jansi found on class path but initialization failed: " + ex)
    }
}