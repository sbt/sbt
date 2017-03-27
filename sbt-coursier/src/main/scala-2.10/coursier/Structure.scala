package coursier

import sbt._

import scala.language.implicitConversions

// things from sbt-structure
object Structure {

  def structure(state: State): BuildStructure =
    sbt.Project.structure(state)

  implicit class `enrich SettingKey`[T](key: SettingKey[T]) {
    def find(state: State): Option[T] =
      key.get(structure(state).data)

    def get(state: State): T =
      find(state).get

    def getOrElse(state: State, default: => T): T =
      find(state).getOrElse(default)
  }

  implicit class `enrich TaskKey`[T](key: TaskKey[T]) {
    def find(state: State): Option[sbt.Task[T]] =
      key.get(structure(state).data)

    def get(state: State): sbt.Task[T] =
      find(state).get

    def forAllProjects(state: State, projects: Seq[ProjectRef]): sbt.Task[Map[ProjectRef, T]] = {
      val tasks = projects.flatMap(p => key.in(p).get(structure(state).data).map(_.map(it => (p, it))))
      std.TaskExtra.joinTasks(tasks).join.map(_.toMap)
    }
  }
}
