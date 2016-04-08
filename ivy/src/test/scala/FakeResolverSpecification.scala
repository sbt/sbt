package sbt

import java.io.File

import org.specs2._

class FakeResolverSpecification extends BaseIvySpecification {
  import FakeResolver._

  def is = s2"""
  This is a specification for the FakeResolver

  The FakeResolver should
    find modules with only one artifact                     $singleArtifact
    find modules with more than one artifact                $multipleArtifacts
  """

  val myModule = ModuleID("org.example", "my-module", "0.0.1-SNAPSHOT", Some("compile"))
  val example = ModuleID("com.example", "example", "1.0.0", Some("compile"))

  def singleArtifact = {
    val m = getModule(myModule)
    val report = ivyUpdate(m)
    val allFiles = getAllFiles(report)

    report.allModules should haveLength(1)
    report.configurations should haveLength(3)
    allFiles should haveLength(1)
    allFiles(1).getName should beEqualTo("artifact1-0.0.1-SNAPSHOT.jar")
  }

  def multipleArtifacts = {
    val m = getModule(example)
    val report = ivyUpdate(m)
    val allFiles = getAllFiles(report).toSet

    report.allModules should haveLength(1)
    report.configurations should haveLength(3)
    allFiles should haveLength(2)
    allFiles map (_.getName) should beEqualTo(Set("artifact1-1.0.0.jar", "artifact2-1.0.0.txt"))
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
    )
  )

  private def fakeResolver = new FakeResolver("FakeResolver", new File("tmp"), modules)
  override def resolvers: Seq[Resolver] = Seq(new RawRepository(fakeResolver))
  private def getModule(myModule: ModuleID): IvySbt#Module = module(defaultModuleId, Seq(myModule), None)
  private def getAllFiles(report: UpdateReport) =
    for {
      conf <- report.configurations
      m <- conf.modules
      (_, f) <- m.artifacts
    } yield f

}