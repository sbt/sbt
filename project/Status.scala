	import sbt._
	import Keys._

object Status
{
	lazy val isSnapshot = SettingKey[Boolean]("is-snapshot")
	lazy val publishStatus = SettingKey[String]("publish-status")

	def settings: Seq[Setting[_]] = Seq(
		isSnapshot <<= version(_ contains "-"),
		publishStatus <<= isSnapshot { snap => if(snap) "snapshots" else "releases" },
		commands += stampVersion
	)
	def stampVersion = Command.command("stamp-version") { state =>
		append((version in ThisBuild ~= stamp) :: Nil, state)
	}
	// TODO: replace with extracted.append from 0.10.1
	def append(settings: Seq[Setting[_]], state: State): State =
	{
		val extracted = Project.extract(state)
			import extracted._
		val append = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, settings)
		val newStructure = Load.reapply(session.original ++ append, structure)
		Project.setProject(session, newStructure, state)
	}
	def stamp(v: String): String =
		if(v endsWith Snapshot)
			(v stripSuffix Snapshot) + "-" + timestampString(System.currentTimeMillis)
		else
			v
	def timestampString(time: Long): String =
	{
		val format = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
		format.format(new java.util.Date(time))
	}
	final val Snapshot = "-SNAPSHOT"
}