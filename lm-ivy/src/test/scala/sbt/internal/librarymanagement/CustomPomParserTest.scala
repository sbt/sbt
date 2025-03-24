package sbt.internal.librarymanagement

import java.io.File
import org.apache.ivy.core.module.descriptor.{ Artifact as IvyArtifact }
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.ResolveOptions
import sbt.librarymanagement.*
import sbt.librarymanagement.ivy.{ InlineIvyConfiguration, IvyPaths }
import sbt.io.IO.withTemporaryDirectory
import sbt.internal.util.ConsoleLogger
import verify.BasicTestSuite

object CustomPomParserTest extends BasicTestSuite {
  test(
    "CustomPomParser should resolve an artifact with packaging 'scala-jar' as a regular jar file."
  ) {
    val log = ConsoleLogger()
    withTemporaryDirectory { cacheDir =>
      val repoUrl = getClass.getResource("/test-maven-repo")
      val local = MavenRepository("Test Repo", repoUrl.toExternalForm)
      val paths = IvyPaths(new File(".").toString, Some(cacheDir.toString))
      val conf = InlineIvyConfiguration()
        .withPaths(paths)
        .withResolvers(Vector(local))
        .withLog(log)
      val ivySbt = new IvySbt(conf)
      val resolveOpts = new ResolveOptions().setConfs(Array("default"))
      val mrid = ModuleRevisionId.newInstance("com.test", "test-artifact", "1.0.0-SNAPSHOT")

      val resolveReport = ivySbt.withIvy(log) { ivy =>
        ivy.resolve(mrid, resolveOpts, true)
      }

      assert(!resolveReport.hasError)
      assert(resolveReport.getArtifacts.size() == 1)
      val artifact: IvyArtifact =
        resolveReport.getArtifacts.asInstanceOf[java.util.List[IvyArtifact]].get(0)
      assert(artifact.getModuleRevisionId == mrid)
      assert(artifact.getExt == "jar")
    }
  }
}
