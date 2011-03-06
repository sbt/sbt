import sbt._
import Keys._
import Project.Initialize

trait Marker
{
	final lazy val Mark = TaskKey[Unit]("mark")
	final def mark: Initialize[Task[Unit]] = mark(baseDirectory)
	final def mark(project: Reference): Initialize[Task[Unit]] = mark(baseDirectory in project)
	final def mark(baseKey: ScopedSetting[File]): Initialize[Task[Unit]] = baseKey map { base =>
		val toMark = base / "ran"
		if(toMark.exists)
			error("Already ran (" + toMark + " exists)")
		else
			IO touch toMark
	}
}