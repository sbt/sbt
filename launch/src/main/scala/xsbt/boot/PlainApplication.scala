package xsbt
package boot

/** A wrapper around 'raw' static methods to meet the sbt application interface. */
class PlainApplication private (mainMethod: java.lang.reflect.Method) extends xsbti.AppMain {
  override def run(configuration: xsbti.AppConfiguration): xsbti.MainResult = {
    // TODO - Figure out if the main method returns an Int...
    val IntClass = classOf[Int]
    val ExitClass = classOf[xsbti.Exit]
    // It seems we may need to wrap exceptions here...
    try mainMethod.getReturnType match {
        case ExitClass =>
          mainMethod.invoke(null, configuration.arguments).asInstanceOf[xsbti.Exit]
        case IntClass =>
          PlainApplication.Exit(mainMethod.invoke(null, configuration.arguments).asInstanceOf[Int])
        case _ => 
          // Here we still invoke, but return 0 if sucessful (no exceptions).
          mainMethod.invoke(null, configuration.arguments)
          PlainApplication.Exit(0)
    } catch {
      // This is only thrown if the underlying reflective call throws.
      // Let's expose the underlying error.
      case e: java.lang.reflect.InvocationTargetException if e.getCause != null =>
        throw e.getCause
    }
    
  }
}
/** An object that lets us detect compatible "plain" applications and launch them reflectively. */
object PlainApplication {
  def isPlainApplication(clazz: Class[_]): Boolean = findMainMethod(clazz).isDefined
  def apply(clazz: Class[_]): xsbti.AppMain =
   findMainMethod(clazz) match {
     case Some(method) => new PlainApplication(method)
     case None => sys.error("Class: " + clazz + " does not have a main method!")
   }
  private def findMainMethod(clazz: Class[_]): Option[java.lang.reflect.Method] =
    try {
      val method = 
        clazz.getMethod("main", classOf[Array[String]])
      if(java.lang.reflect.Modifier.isStatic(method.getModifiers)) Some(method)
      else None
    } catch {
      case n: NoSuchMethodException => None
    }

  case class Exit(code: Int) extends xsbti.Exit
}
