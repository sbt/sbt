package xsbt.boot

import java.io.{ File, InputStream }
import java.net.URL
import java.util.Properties
import xsbti._
import org.specs2._
import mutable.Specification
import sbt.IO.{ createDirectory, touch, withTemporaryDirectory }

object ConfigurationParserTest extends Specification {
  "Configuration Parser" should {
    "Correctly parse bootOnly" in {

      repoFileContains("""|[repositories]
                                            |  local: bootOnly""".stripMargin,
        Repository.Predefined("local", true))

      repoFileContains("""|[repositories]
                                            |  local""".stripMargin,
        Repository.Predefined("local", false))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org""".stripMargin,
        Repository.Maven("id", new URL("http://repo1.maven.org"), false))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, bootOnly""".stripMargin,
        Repository.Maven("id", new URL("http://repo1.maven.org"), true))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath]""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[orgPath]", false, false))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], mavenCompatible""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[orgPath]", true, false))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], mavenCompatible, bootOnly""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[orgPath]", true, true))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], bootOnly, mavenCompatible""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[orgPath]", true, true))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], bootOnly""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[orgPath]", false, true))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], [artPath]""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[artPath]", false, false))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], [artPath], descriptorOptional""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[artPath]", false, false, true, false))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], [artPath], descriptorOptional, skipConsistencyCheck""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[artPath]", false, false, true, true))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], [artPath], skipConsistencyCheck, descriptorOptional""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[artPath]", false, false, true, true))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], [artPath], skipConsistencyCheck, descriptorOptional, mavenCompatible, bootOnly""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[artPath]", true, true, true, true))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], [artPath], bootOnly""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[artPath]", false, true))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], [artPath], bootOnly, mavenCompatible""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[artPath]", true, true))

      repoFileContains("""|[repositories]
                                            |  id: http://repo1.maven.org, [orgPath], [artPath], mavenCompatible, bootOnly""".stripMargin,
        Repository.Ivy("id", new URL("http://repo1.maven.org"), "[orgPath]", "[artPath]", true, true))

    }
  }

  def repoFileContains(file: String, repo: Repository.Repository) =
    loadRepoFile(file) must contain(repo)

  def loadRepoFile(file: String) =
    (new ConfigurationParser) readRepositoriesConfig file
}
