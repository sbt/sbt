import sbt._

object Q extends AutoPlugin {
  override val requires = plugins.JvmPlugin
  override val trigger = allRequirements

  object autoImport {
    val zyx = -3
  }
}

package name.example {
  object P extends AutoPlugin {
    override val requires = plugins.JvmPlugin
    override val trigger = allRequirements

    object autoImport {
      val xyz = 3
      val checkMaxErrors = taskKey[Unit]("")
      val checkName = taskKey[Unit]("")
    }

    import autoImport._
    override def projectSettings = Seq[Setting[_]](
      checkMaxErrors := (Keys.maxErrors map { me => assert(me == xyz, "Expected maxErrors to be " + xyz + ", but it was " + me ) }).value,
      checkName := (Keys.name map { n => assert(n == "Demo", "Expected name to be 'Demo', but it was '" + n + "'" ) }).value
    )
  }
}
