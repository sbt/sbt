import java.io.File
import org.apache.ivy.core.module.descriptor.{ Artifact => IvyArtifact }
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.ResolveOptions
import org.specs2.mutable.Specification
import sbt._
import IO.withTemporaryDirectory

object CustomPomParserTest extends Specification {

  "CustomPomParser" should {
    "resolve an artifact with packaging 'scala-jar' as a regular jar file." in {
      val log = ConsoleLogger()
      withTemporaryDirectory { cacheDir =>
        val repoUrl = getClass.getResource("/test-maven-repo")
        val local = MavenRepository("Test Repo", repoUrl.toExternalForm)
        val paths = new IvyPaths(new File("."), Some(cacheDir))
        val conf =
          new InlineIvyConfiguration(paths, Seq(local), Nil, Nil, false, None, Seq("sha1", "md5"), None, log)
        val ivySbt = new IvySbt(conf)
        val resolveOpts = new ResolveOptions().setConfs(Array("default"))
        val mrid = ModuleRevisionId.newInstance("com.test", "test-artifact", "1.0.0-SNAPSHOT")

        val resolveReport = ivySbt.withIvy(log) { ivy =>
          ivy.resolve(mrid, resolveOpts, true)
        }

        resolveReport.hasError must beFalse
        resolveReport.getArtifacts.size() must beEqualTo(1)
        val artifact: IvyArtifact = resolveReport.getArtifacts.asInstanceOf[java.util.List[IvyArtifact]].get(0)
        artifact.getModuleRevisionId must beEqualTo(mrid)
        artifact.getExt must beEqualTo("jar")
      }

    }
  }

}
