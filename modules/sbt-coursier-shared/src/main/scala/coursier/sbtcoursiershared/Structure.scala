package coursier.sbtcoursiershared

import sbt._

object Structure {

  def allRecursiveInterDependencies(state: sbt.State, projectRef: sbt.ProjectRef) = {

    def dependencies(map: Map[String, Seq[String]], id: String): Set[String] = {

      def helper(map: Map[String, Seq[String]], acc: Set[String]): Set[String] =
        if (acc.exists(map.contains)) {
          val (kept, rem) = map.partition { case (k, _) => acc(k) }
          helper(rem, acc ++ kept.valuesIterator.flatten)
        } else
          acc

      helper(map - id, map.getOrElse(id, Nil).toSet)
    }

    val allProjectsDeps =
      for (p <- structure(state).allProjects)
        yield p.id -> p.dependencies.map(_.project.project)

    val deps = dependencies(allProjectsDeps.toMap, projectRef.project)

    structure(state).allProjectRefs.filter(p => deps(p.project))
  }

  // vv things from sbt-structure vv

  def structure(state: State) =
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

    // ^^ things from sbt-structure ^^

    def forAllProjectsOpt(state: State, projects: Seq[ProjectRef]): sbt.Task[Map[ProjectRef, Option[T]]] = {
      val settings = structure(state).data
      val tasks = projects.map { p =>
        val taskOpt = key.in(p).get(settings)
        taskOpt match {
          case None =>
            Def.task(p -> Option.empty[T]).evaluate(settings)
          case Some(t) =>
            t.map(p -> Option(_))
        }
      }
      std.TaskExtra.joinTasks(tasks).join.map(_.toMap)
    }
  }
}
