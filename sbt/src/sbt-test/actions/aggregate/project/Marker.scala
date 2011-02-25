import sbt._
import Keys._
import Project.Initialize

trait Marker
{
	final lazy val Mark = TaskKey[Unit]("mark")
	final def mark: Initialize[Task[Unit]] = mark(Base)
	final def mark(project: ProjectRef): Initialize[Task[Unit]] = mark(Base in project)
	final def mark(baseKey: ScopedSetting[File]): Initialize[Task[Unit]] = baseKey map { base =>
		val toMark = base / "ran"
		if(toMark.exists)
			error("Already ran (" + toMark + " exists)")
		else
			IO touch toMark
	}
}