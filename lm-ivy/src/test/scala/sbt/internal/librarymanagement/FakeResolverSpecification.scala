package sbt
package internal
package librarymanagement

import java.io.File

import sbt.librarymanagement.{ ModuleID, RawRepository, Resolver, UpdateReport, ResolveException }

object FakeResolverSpecification extends BaseIvySpecification {
  import FakeResolver.*

  val myModule =
    ModuleID("org.example", "my-module", "0.0.1-SNAPSHOT").withConfigurations(Some("compile"))
  val example = ModuleID("com.example", "example", "1.0.0").withConfigurations(Some("compile"))
  val anotherExample =
    ModuleID("com.example", "another-example", "1.0.0").withConfigurations(Some("compile"))
  val nonExisting =
    ModuleID("com.example", "does-not-exist", "1.2.3").withConfigurations(Some("compile"))

  test("The FakeResolver should find modules with only one artifact") {
    val m = getModule(myModule)
    val report = ivyUpdate(m)
    val allFiles = getAllFiles(report)

    assert(report.allModules.length == 1)
    assert(report.allModuleReports.length == 1)
    assert(report.configurations.length == 3)
    assert(allFiles.toSet.size == 1)
    assert(allFiles(1).getName == "artifact1-0.0.1-SNAPSHOT.jar")
  }

  test("it should find modules with more than one artifact") {
    val m = getModule(example)
    val report = ivyUpdate(m)
    val allFiles = getAllFiles(report).toSet

    assert(report.allModules.length == 1)
    assert(report.allModuleReports.length == 1)
    assert(report.configurations.length == 3)
    assert(allFiles.toSet.size == 2)
    assert(allFiles.map(_.getName) == Set("artifact1-1.0.0.jar", "artifact2-1.0.0.txt"))
  }

  test("it should fail gracefully when asked for unknown modules") {
    val m = getModule(nonExisting)
    intercept[ResolveException] {
      ivyUpdate(m)
      ()
    }
  }

  test("it should fail gracefully when some artifacts cannot be found") {
    val m = getModule(anotherExample)
    intercept[ResolveException] {
      ivyUpdate(m)
      ()
    }
  }

  private def artifact1 = new File(getClass.getResource("/artifact1.jar").toURI.getPath)
  private def artifact2 = new File(getClass.getResource("/artifact2.txt").toURI.getPath)

  private def modules = Map(
    ("org.example", "my-module", "0.0.1-SNAPSHOT") -> List(
      FakeArtifact("artifact1", "jar", "jar", artifact1)
    ),
    ("com.example", "example", "1.0.0") -> List(
      FakeArtifact("artifact1", "jar", "jar", artifact1),
      FakeArtifact("artifact2", "txt", "txt", artifact2)
    ),
    ("com.example", "another-example", "1.0.0") -> List(
      FakeArtifact("artifact1", "jar", "jar", artifact1),
      FakeArtifact("non-existing", "txt", "txt", new File("non-existing-file"))
    )
  )

  private def fakeResolver = new FakeResolver("FakeResolver", new File("tmp"), modules)
  override def resolvers: Vector[Resolver] =
    Vector(new RawRepository(fakeResolver, fakeResolver.getName))
  private def getModule(myModule: ModuleID): IvySbt#Module =
    module(defaultModuleId, Vector(myModule), None)
  private def getAllFiles(report: UpdateReport) =
    for {
      conf <- report.configurations
      m <- conf.modules
      (_, f) <- m.artifacts
    } yield f

}
