import sbt._
import Keys._

object StatusPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  object autoImport {
    lazy val publishStatus = settingKey[String]("possible values are snapshots or releases.")
  }

  import autoImport._

  override def buildSettings: Seq[Setting[_]] = Seq(
    isSnapshot := {
      val SnapshotQualifier = """(.+)(-.*SNAPSHOT)(.*)""".r
      val v = version.value
      v match {
        case SnapshotQualifier(_, _, _) => true
        case _                          => false
      }
    },
    publishStatus := {
      if (isSnapshot.value) "snapshots"
      else "releases"
    },
    commands += stampVersion
  )
  def stampVersion = Command.command("stamp-version") { state =>
    val extracted = Project.extract(state)
    val status = extracted.get(publishStatus)
    // Set new version AND lock down the publishStatus to what it was, as
    // our release regexes no longer support ivy data format, due to other issues.
    extracted.append((version in ThisBuild ~= stamp) ::
                       (publishStatus in ThisBuild := status) ::
                       Nil,
                     state)
  }
  def stamp(v: String): String = {
    val Snapshot = "-SNAPSHOT"
    if (v endsWith Snapshot)
      (v stripSuffix Snapshot) + "-" + timestampString(System.currentTimeMillis)
    else sys.error("Release version '" + v + "' cannot be stamped")
  }
  def timestampString(time: Long): String = {
    val format = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
    format.format(new java.util.Date(time))
  }
}
