package sbt

import sbt.ivyint.MergedDescriptors
import org.specs2._
import org.apache.ivy.core.module.descriptor.{ DependencyArtifactDescriptor, DependencyDescriptor }

class MergeDescriptorSpec extends BaseIvySpecification {
  def is = args(sequential = true) ^ s2"""

  This is a specification to check the merge descriptor

  Merging duplicate dependencies should
    work                                                        $e1
                                                                """

  def guavaTest = ModuleID("com.google.guava", "guava-tests", "18.0", configurations = Some("compile"))
  def guavaTestTests =
    ModuleID("com.google.guava", "guava-tests", "18.0", configurations = Some("test")).classifier("tests")
  def defaultOptions = EvictionWarningOptions.default

  import ShowLines._

  def e1 = {
    cleanIvyCache()
    val m = module(
      ModuleID("com.example", "foo", "0.1.0", Some("compile")),
      Seq(guavaTest, guavaTestTests),
      None,
      UpdateOptions()
    )
    m.withModule(log) {
      case (ivy, md, _) =>
        val deps = md.getDependencies
        deps.headOption.getOrElse(sys.error("Dependencies not found")) match {
          case dd @ MergedDescriptors(dd0, dd1) =>
            val arts = dd.getAllDependencyArtifacts
            val a0: DependencyArtifactDescriptor = arts.toList(0)
            val a1: DependencyArtifactDescriptor = arts.toList(1)
            val configs0 = a0.getConfigurations.toList
            val configs1 = a1.getConfigurations.toList
            (configs0 must_== List("compile")) and
              (configs1 must_== List("test"))
        }
    }
  }
}
