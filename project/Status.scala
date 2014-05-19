import sbt._
import Keys._
import java.util.regex.Pattern

object Status {
  lazy val publishStatus = SettingKey[String]("publish-status")

  def settings: Seq[Setting[_]] = Seq(
    isSnapshot <<= version(v => v.contains("-") && snapshotQualifier(v)),
    publishStatus <<= isSnapshot { snap => if (snap) "snapshots" else "releases" },
    commands += stampVersion
  )
  def stampVersion = Command.command("stamp-version") { state =>
    Project.extract(state).append((version in ThisBuild ~= stamp) :: Nil, state)
  }
  def stamp(v: String): String =
    if (v endsWith Snapshot)
      (v stripSuffix Snapshot) + "-" + timestampString(System.currentTimeMillis)
    else
      error("Release version '" + v + "' cannot be stamped")
  def timestampString(time: Long): String =
    {
      val format = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
      format.format(new java.util.Date(time))
    }
  final val Snapshot = "-SNAPSHOT"
  // NOte: This moved into sbt itself...  But we need to add semantic knowledge of how
  // we stamp our nightlies.
  def snapshotQualifier(v: String) = Pattern.matches(""".+-.*SNAPSHOT.*""", v)
}