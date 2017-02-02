package coursier

import sbt._

import scala.language.implicitConversions

// things from sbt-structure
object Structure {
  import Def.Initialize._

  def structure(state: State): Load.BuildStructure =
    sbt.Project.structure(state)

  implicit def `enrich SettingKey`[T](key: SettingKey[T]) = new {
    def find(state: State): Option[T] =
      key.get(structure(state).data)

    def get(state: State): T =
      find(state).get

    def getOrElse(state: State, default: => T): T =
      find(state).getOrElse(default)
  }

  implicit def `enrich TaskKey`[T](key: TaskKey[T]) = new {
    def find(state: State): Option[sbt.Task[T]] =
      key.get(structure(state).data)

    def get(state: State): sbt.Task[T] =
      find(state).get

    def forAllProjects(state: State, projects: Seq[ProjectRef]): sbt.Task[Map[ProjectRef, T]] = {
      val tasks = projects.flatMap(p => key.in(p).get(structure(state).data).map(_.map(it => (p, it))))
      std.TaskExtra.joinTasks(tasks).join.map(_.toMap)
    }

    def forAllConfigurations(state: State, configurations: Seq[sbt.Configuration]): sbt.Task[Map[sbt.Configuration, T]] = {
      val tasks = configurations.flatMap(c => key.in(c).get(structure(state).data).map(_.map(it => (c, it))))
      std.TaskExtra.joinTasks(tasks).join.map(_.toMap)
    }
  }
}
