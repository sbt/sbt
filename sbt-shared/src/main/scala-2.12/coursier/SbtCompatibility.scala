package coursier

object SbtCompatibility {

  val ConfigRef = sbt.librarymanagement.ConfigRef
  type ConfigRef = sbt.librarymanagement.ConfigRef

  val GetClassifiersModule = sbt.librarymanagement.GetClassifiersModule
  type GetClassifiersModule = sbt.librarymanagement.GetClassifiersModule

  object SbtPomExtraProperties {
    def POM_INFO_KEY_PREFIX = sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties.POM_INFO_KEY_PREFIX
  }

  type MavenRepository = sbt.librarymanagement.MavenRepository

  type IvySbt = sbt.internal.librarymanagement.IvySbt

  lazy val needsIvyXmlLocal = Seq(sbt.Keys.publishLocalConfiguration) ++ {
    try {
      val cls = sbt.Keys.getClass
      val m = cls.getMethod("makeIvyXmlLocalConfiguration")
      val task = m.invoke(sbt.Keys).asInstanceOf[sbt.TaskKey[sbt.PublishConfiguration]]
      List(task)
    } catch {
      case _: Throwable => // FIXME Too wide
        Nil
    }
  }

  lazy val needsIvyXml = Seq(sbt.Keys.publishConfiguration) ++ {
    try {
      val cls = sbt.Keys.getClass
      val m = cls.getMethod("makeIvyXmlConfiguration")
      val task = m.invoke(sbt.Keys).asInstanceOf[sbt.TaskKey[sbt.PublishConfiguration]]
      List(task)
    } catch {
      case _: Throwable => // FIXME Too wide
        Nil
    }
  }

}
