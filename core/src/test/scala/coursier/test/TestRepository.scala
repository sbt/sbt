package coursier
package test

import coursier.core.{Versions, CachePolicy}

import scalaz.{-\/, \/, EitherT}
import scalaz.concurrent.Task
import scalaz.Scalaz._

class TestRepository(projects: Map[Module, Project]) extends Repository {
  def find(module: Module, cachePolicy: CachePolicy) =
    EitherT(Task.now(
      projects.get(module).toRightDisjunction("Not found")
    ))
  def versions(organization: String, name: String, cachePolicy: CachePolicy) =
    EitherT(Task.now[String \/ Versions](
      -\/("Not supported")
    ))
}
