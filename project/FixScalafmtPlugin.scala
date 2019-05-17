import sbt._
import Keys._
import org.scalafmt.sbt.ScalafmtPlugin
import ScalafmtPlugin.autoImport._

object FixScalafmtPlugin extends AutoPlugin {
  override def requires = ScalafmtPlugin
  override def trigger = allRequirements

  val ScalaFmtTag = ConcurrentRestrictions.Tag("scalafmt")

  override def globalSettings: Seq[Def.Setting[_]] =
    Seq(
      concurrentRestrictions += Tags.limit(ScalaFmtTag, 1)
    )

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      scalafmtCheckAll := (scalafmtCheckAll.tag(ScalaFmtTag)).value,
      Compile / scalafmtCheck := ((Compile / scalafmtCheck).tag(ScalaFmtTag)).value,
      Test / scalafmtCheck := ((Test / scalafmtCheck).tag(ScalaFmtTag)).value,
    )
}
