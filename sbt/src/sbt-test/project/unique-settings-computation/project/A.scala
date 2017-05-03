import java.util.concurrent.atomic.AtomicInteger
import sbt._, Keys._

object A extends AutoPlugin {
  object autoImport {
    lazy val foo = settingKey[String]("Foo.")
  }
  import autoImport._
  override def trigger = allRequirements

  override def buildSettings: Seq[Setting[_]] =
    (foo := s"build ${buildCount.getAndIncrement}") ::
    Nil

  override def globalSettings: Seq[Setting[_]] =
    (foo := s"global ${globalCount.getAndIncrement}") ::
    (commands += setUpScripted) ::
    Nil

  def setUpScripted = Command.command("setUpScripted") { (state0: State) =>
    Project.extract(state0).append(name := "foo", state0)
  }

  // used to ensure the build-level and global settings are only added once
  private[this] val buildCount = new AtomicInteger(0)
  private[this] val globalCount = new AtomicInteger(0)
}
