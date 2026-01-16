package sbt.internal.librarymanagement

import sbt.io.*
import sbt.io.syntax.*
import sbt.util.Level
import sbt.librarymanagement.*
import sbt.librarymanagement.syntax.*
import java.nio.file.Paths

//by default this test is ignored
//to run this you need to change "repo" to point to some sftp repository which contains a dependency referring a dependency in same repo
//it will then attempt to authenticate via key file and fetch the dependency specified via "org" and "module"
object SftpRepoSpec extends BaseIvySpecification {
  val repo: Option[String] = None
//  val repo: Option[String] = Some("some repo")
  // a dependency which depends on another in the repo
  def org(repo: String) = s"com.${repo}"
  def module(org: String) = org % "some-lib" % "version"

  override def resolvers = {
    implicit val patterns = Resolver.defaultIvyPatterns
    repo.map { repo =>
      val privateKeyFile = Paths.get(sys.env("HOME"), ".ssh", s"id_${repo}").toFile
      Resolver.sftp(repo, s"repo.${repo}.com", 2222).as(repo, privateKeyFile)
    }.toVector ++ super.resolvers
  }

  test("resolving multiple deps from sftp repo should not hang or fail") {
    repo match {
      case Some(repo) =>
        IO.delete(currentTarget / "cache" / org(repo))
        //    log.setLevel(Level.Debug)
        lmEngine().retrieve(module(org(repo)), scalaModuleInfo = None, currentTarget, log) match {
          case Right(v) => log.debug(v.toString())
          case Left(e) =>
            log.log(Level.Error, e.failedPaths.toString())
            throw e.resolveException
        }
      case None => log.info(s"skipped ${getClass}")
    }
  }
}
