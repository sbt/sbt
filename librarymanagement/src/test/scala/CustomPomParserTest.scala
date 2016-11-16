package sbt.internal.librarymanagement

import java.io.File
import org.apache.ivy.core.module.descriptor.{ Artifact => IvyArtifact }
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.ResolveOptions
import sbt.librarymanagement._
import sbt.io.IO.withTemporaryDirectory
import sbt.internal.util.ConsoleLogger

class CustomPomParserTest extends UnitSpec {
  "CustomPomParser" should "resolve an artifact with packaging 'scala-jar' as a regular jar file." in {
    val log = ConsoleLogger()
    withTemporaryDirectory { cacheDir =>
      val repoUrl = getClass.getResource("/test-maven-repo")
      val local = MavenRepository("Test Repo", repoUrl.toExternalForm)
      val paths = new IvyPaths(new File("."), Some(cacheDir))
      val conf = new InlineIvyConfiguration(paths, Vector(local), Vector.empty, Vector.empty, false, None, Vector("sha1", "md5"), None, UpdateOptions(), log)
      val ivySbt = new IvySbt(conf, DefaultFileToStore)
      val resolveOpts = new ResolveOptions().setConfs(Array("default"))
      val mrid = ModuleRevisionId.newInstance("com.test", "test-artifact", "1.0.0-SNAPSHOT")

      val resolveReport = ivySbt.withIvy(log) { ivy =>
        ivy.resolve(mrid, resolveOpts, true)
      }

      resolveReport.hasError shouldBe false
      resolveReport.getArtifacts.size() shouldBe 1
      val artifact: IvyArtifact = resolveReport.getArtifacts.asInstanceOf[java.util.List[IvyArtifact]].get(0)
      artifact.getModuleRevisionId shouldBe mrid
      artifact.getExt shouldBe "jar"
    }
  }
}
