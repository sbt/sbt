package sbt

import org.specs2._
import sbt.BaseIvySpecification
import sbt.EvictionWarningOptions
import sbt.Level
import sbt.ModuleID
import sbt.ShowLines
import sbt.UpdateOptions

class MavenResolutionSpec extends BaseIvySpecification {
  def is = args(sequential = true) ^ s2"""

  This is a specification to check the maven resolution

  Resolving a maven dependency should
     resovle transitive maven dependencies                        $resolveTransitiveMavenDependency
                                                                """

  def akkaActor = ModuleID("com.typesafe.akka", "akka-actor_2.11", "2.3.8", Some("compile"))

  import ShowLines._

  def resolveTransitiveMavenDependency = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(akkaActor), Some("2.10.2"), UpdateOptions())
    val report = ivyUpdate(m)
    println(report)
    val jars =
      for {
        conf <- report.configurations
        if conf.configuration == "compile"
        m <- conf.modules
        if m.module.name == "scala-library"
        (a, f) <- m.artifacts
        if a.extension == "jar"
      } yield f
    (report.configurations.size must_== 3) and
      (jars.forall(_.exists) must beTrue)

  }

}
