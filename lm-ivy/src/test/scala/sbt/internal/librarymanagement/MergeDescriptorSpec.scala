package sbt.internal.librarymanagement

import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor
import sbt.librarymanagement.*
import sbt.librarymanagement.ivy.UpdateOptions
import sbt.internal.librarymanagement.ivyint.*

object MergeDescriptorSpec extends BaseIvySpecification {
  test("Merging duplicate dependencies should work") {
    cleanIvyCache()
    val m = module(
      ModuleID("com.example", "foo", "0.1.0").withConfigurations(Some("compile")),
      Vector(guavaTest, guavaTestTests),
      None,
      UpdateOptions()
    )
    m.withModule(log) { case (_, md, _) =>
      val deps = md.getDependencies
      assert(deps.size == 1)
      deps.headOption.getOrElse(sys.error("Dependencies not found")) match {
        case dd @ MergedDescriptors(_, _) =>
          val arts = dd.getAllDependencyArtifacts
          val a0: DependencyArtifactDescriptor = arts.toList(0)
          val a1: DependencyArtifactDescriptor = arts.toList(1)
          val configs0 = a0.getConfigurations.toList
          val configs1 = a1.getConfigurations.toList
          assert(configs0 == List("compile"))
          assert(configs1 == List("test"))
      }
    }
  }
  def guavaTest =
    ModuleID("com.google.guava", "guava-tests", "18.0").withConfigurations(Option("compile"))
  def guavaTestTests =
    ModuleID("com.google.guava", "guava-tests", "18.0")
      .withConfigurations(Option("test"))
      .classifier("tests")
  def defaultOptions = EvictionWarningOptions.default

}
