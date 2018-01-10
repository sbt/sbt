package coursier

import sbt._, Keys._

object SbtCompatibility {
  lazy val needsIvyXmlLocal = Seq(publishLocalConfiguration) ++ getPubConf("makeIvyXmlLocalConfiguration")
  lazy val needsIvyXml = Seq(publishConfiguration) ++ getPubConf("makeIvyXmlConfiguration")

  private[this] def getPubConf(method: String) =
    try {
      val cls = Keys.getClass
      val m = cls.getMethod(method)
      val task = m.invoke(Keys).asInstanceOf[TaskKey[PublishConfiguration]]
      List(task)
    } catch {
      case _: Throwable => // FIXME Too wide
        Nil
    }
}
