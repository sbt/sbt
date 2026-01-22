package lmcoursier.internal

import coursier.core.{ Configuration, Dependency, Repository }
import java.security.MessageDigest
import scala.collection.immutable.Seq

object BuildClock {

  def compute(
      dependencies: Seq[(Configuration, Dependency)],
      repositories: Seq[Repository],
      scalaVersion: Option[String],
      params: ResolutionParams
  ): String = {
    val digest = MessageDigest.getInstance("SHA-1")

    dependencies.sortBy(d => (d._1.value, d._2.module.toString, d._2.version)).foreach {
      case (config, dep) =>
        digest.update(config.value.getBytes("UTF-8"))
        digest.update(dep.module.organization.value.getBytes("UTF-8"))
        digest.update(dep.module.name.value.getBytes("UTF-8"))
        digest.update(dep.version.getBytes("UTF-8"))
        digest.update(dep.configuration.value.getBytes("UTF-8"))
    }

    repositories.foreach { repo =>
      digest.update(repo.toString.getBytes("UTF-8"))
    }

    scalaVersion.foreach { sv =>
      digest.update(sv.getBytes("UTF-8"))
    }

    digest.update(params.params.maxIterations.toString.getBytes("UTF-8"))

    params.params.forceVersion.toSeq.sortBy(_._1.toString).foreach { case (mod, ver) =>
      digest.update(mod.toString.getBytes("UTF-8"))
      digest.update(ver.getBytes("UTF-8"))
    }

    params.params.exclusions.toSeq.sortBy(e => (e._1.value, e._2.value)).foreach {
      case (org, name) =>
        digest.update(s"exclude:${org.value}:${name.value}".getBytes("UTF-8"))
    }

    params.strictOpt.foreach { strict =>
      digest.update(s"strict:${strict.toString}".getBytes("UTF-8"))
    }

    val hashBytes = digest.digest()
    hashBytes.map("%02x".format(_)).mkString
  }

  def matches(
      lockFileData: LockFileData,
      dependencies: Seq[(Configuration, Dependency)],
      repositories: Seq[Repository],
      scalaVersion: Option[String],
      params: ResolutionParams
  ): Boolean = {
    val currentClock = compute(dependencies, repositories, scalaVersion, params)
    lockFileData.buildClock == currentClock
  }
}
