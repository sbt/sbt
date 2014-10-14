package xsbt.boot

import xsbti._
import org.specs2._
import mutable.Specification
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.{ FileSystemResolver, URLResolver, AbstractPatternsBasedResolver }

object UpdateTests extends Specification {
  val configuration = new UpdateConfiguration(new java.io.File("/tmp"), None, "org.scala", None, List.empty[Repository], List.empty[String])
  val update = new Update(configuration)
  val ivySettings = new IvySettings()

  val fileResolver = {
    val resolver = new FileSystemResolver
    resolver.setName("foo")
    resolver.addIvyPattern("/tmp/cache/[orgPath]/[module]/[revision]/[module]-[revision].ivy")
    resolver.addArtifactPattern("/tmp/cache/[orgPath]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]")
    resolver
  }

  val urlResolver = {
    val resolver = new URLResolver
    resolver.setName("foo")
    resolver.addIvyPattern("http://artifactory:8081/release/[orgPath]/[module]/[revision]/[module]-[revision].ivy")
    resolver.addArtifactPattern("http://artifactory:8081/release/[orgPath]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]")
    resolver
  }

  "Update.toIvyRepository" should {
    "convert IvyRepository" in {
      "local file path to FileSystemResolver" in {
        converstionEquals("file:///tmp/cache", fileResolver)
        converstionEquals("file:///tmp/cache/", fileResolver)
      }

      "others to UrlResolver" in {
        converstionEquals("http://artifactory:8081/release", urlResolver)
        converstionEquals("http://artifactory:8081/release/", urlResolver)
      }
    }
  }

  def converstionEquals(base: String, expected: AbstractPatternsBasedResolver) = {
    val repo = Repository.Ivy("foo", new java.net.URL(base), "[orgPath]/[module]/[revision]/[module]-[revision].ivy", "[orgPath]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]", false)
    val resolver = update.toIvyRepository(ivySettings, repo)

    resolver.getClass mustEqual expected.getClass
    resolver.getIvyPatterns mustEqual expected.getIvyPatterns
    resolver.getArtifactPatterns mustEqual expected.getArtifactPatterns
  }

}