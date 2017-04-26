package sbt
package internal
package librarymanagement

import java.io.File

import sbt.librarymanagement.{ ModuleID, RawRepository, Resolver, UpdateReport }

class FakeResolverSpecification extends BaseIvySpecification {
  import FakeResolver._

  val myModule =
    ModuleID("org.example", "my-module", "0.0.1-SNAPSHOT").withConfigurations(Some("compile"))
  val example = ModuleID("com.example", "example", "1.0.0").withConfigurations(Some("compile"))
  val anotherExample =
    ModuleID("com.example", "another-example", "1.0.0").withConfigurations(Some("compile"))
  val nonExisting =
    ModuleID("com.example", "does-not-exist", "1.2.3").withConfigurations(Some("compile"))

  "The FakeResolver" should "find modules with only one artifact" in {
    val m = getModule(myModule)
    val report = ivyUpdate(m)
    val allFiles = getAllFiles(report)

    report.allModules.length shouldBe 1
    report.configurations.length shouldBe 3
    allFiles.toSet.size shouldBe 1
    allFiles(1).getName shouldBe "artifact1-0.0.1-SNAPSHOT.jar"
  }

  it should "find modules with more than one artifact" in {
    val m = getModule(example)
    val report = ivyUpdate(m)
    val allFiles = getAllFiles(report).toSet

    report.allModules.length shouldBe 1
    report.configurations.length shouldBe 3
    allFiles.toSet.size shouldBe 2
    allFiles map (_.getName) shouldBe Set("artifact1-1.0.0.jar", "artifact2-1.0.0.txt")
  }

  it should "fail gracefully when asked for unknown modules" in {
    val m = getModule(nonExisting)
    a[ResolveException] should be thrownBy ivyUpdate(m)
  }

  it should "fail gracefully when some artifacts cannot be found" in {
    val m = getModule(anotherExample)
    the[ResolveException] thrownBy ivyUpdate(m) should have message "download failed: com.example#another-example;1.0.0!non-existing.txt"
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
  override def resolvers: Vector[Resolver] = Vector(new RawRepository(fakeResolver))
  private def getModule(myModule: ModuleID): IvySbt#Module =
    module(defaultModuleId, Vector(myModule), None)
  private def getAllFiles(report: UpdateReport) =
    for {
      conf <- report.configurations
      m <- conf.modules
      (_, f) <- m.artifacts
    } yield f

}
