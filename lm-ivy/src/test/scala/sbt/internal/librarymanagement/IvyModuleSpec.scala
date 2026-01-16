package sbt.internal.librarymanagement

import sbt.internal.librarymanagement.mavenint.PomExtraDependencyAttributes.{
  SbtVersionKey,
  ScalaVersionKey
}
import sbt.librarymanagement.{ CrossVersion, ModuleDescriptorConfiguration }

object IvyModuleSpec extends BaseIvySpecification {

  test("The Scala binary version of a Scala module should be appended to its name") {
    val m = module(
      defaultModuleId.withCrossVersion(CrossVersion.Binary()),
      Vector.empty,
      Some("2.13.10")
    )
    m.moduleSettings match {
      case configuration: ModuleDescriptorConfiguration =>
        assert(configuration.module.name == "foo_2.13")
      case _ => fail()
    }
  }

  test("The sbt cross-version should be appended to the name of an sbt plugin") {
    val m = module(
      defaultModuleId.extra(SbtVersionKey -> "1.0", ScalaVersionKey -> "2.12"),
      Vector.empty,
      Some("2.12.17"),
      appendSbtCrossVersion = true
    )
    m.moduleSettings match {
      case configuration: ModuleDescriptorConfiguration =>
        assert(configuration.module.name == "foo_2.12_1.0")
      case _ => fail()
    }
  }

}
