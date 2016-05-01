package sbt
package internal
package librarymanagement

import sbt.internal.util.UnitSpec

import java.net.URL

/**
 * Tests that we can correctly parse repositories definitions.
 */
class RepositoriesParserSpecification extends UnitSpec {
  import RepositoriesParser._

  "The RepositoriesParser" should "check that repositories file starts with [repositories]" in {
    val file = """local
                 |maven-central""".stripMargin
    a[Exception] should be thrownBy RepositoriesParser(file)
  }

  it should "parse the local repository" in {
    val file = """[repositories]
                 |  local""".stripMargin
    val repos = RepositoriesParser(file)
    repos.size shouldBe 1
    repos(0) shouldBe PredefinedRepository(xsbti.Predefined.Local)

  }

  it should "parse the local maven repository" in {
    val file = """[repositories]
                 |  maven-local""".stripMargin
    val repos = RepositoriesParser(file)
    repos.size shouldBe 1
    repos(0) shouldBe PredefinedRepository(xsbti.Predefined.MavenLocal)
  }

  it should "parse Maven Central repository" in {
    val file = """[repositories]
                 |  maven-central""".stripMargin
    val repos = RepositoriesParser(file)
    repos.size shouldBe 1
    repos(0) shouldBe PredefinedRepository(xsbti.Predefined.MavenCentral)
  }

  it should "parse simple Maven repository" in {
    val file = """[repositories]
                 |  mavenRepo: https://repo1.maven.org""".stripMargin
    val repos = RepositoriesParser(file)
    repos.size shouldBe 1
    repos(0) shouldBe MavenRepository("mavenRepo", new URL("https://repo1.maven.org"))
  }

  it should "parse `bootOnly` option" in {
    val file = """[repositories]
                 |  ivyRepo: https://repo1.maven.org, [orgPath], bootOnly""".stripMargin
    val repos = RepositoriesParser(file)
    val expected =
      IvyRepository("ivyRepo", new URL("https://repo1.maven.org"), "[orgPath]", "[orgPath]",
        mavenCompatible = false,
        skipConsistencyCheck = false,
        descriptorOptional = false,
        bootOnly = true)
    repos.size shouldBe 1
    repos(0) shouldBe expected
  }

  it should "parse `mavenCompatible` option" in {
    val file = """[repositories]
                 |  ivyRepo: https://repo1.maven.org, [orgPath], mavenCompatible""".stripMargin
    val repos = RepositoriesParser(file)
    val expected =
      IvyRepository("ivyRepo", new URL("https://repo1.maven.org"), "[orgPath]", "[orgPath]",
        mavenCompatible = true,
        skipConsistencyCheck = false,
        descriptorOptional = false,
        bootOnly = false)
    repos.size shouldBe 1
    repos(0) shouldBe expected
  }

  it should "parse `skipConsistencyCheck` option" in {
    val file = """[repositories]
                 |  ivyRepo: https://repo1.maven.org, [orgPath], skipConsistencyCheck""".stripMargin
    val repos = RepositoriesParser(file)
    val expected =
      IvyRepository("ivyRepo", new URL("https://repo1.maven.org"), "[orgPath]", "[orgPath]",
        mavenCompatible = false,
        skipConsistencyCheck = true,
        descriptorOptional = false,
        bootOnly = false)
    repos.size shouldBe 1
    repos(0) shouldBe expected
  }

  it should "parse `descriptorOptional` option" in {
    val file = """[repositories]
                 |  ivyRepo: https://repo1.maven.org, [orgPath], descriptorOptional""".stripMargin
    val repos = RepositoriesParser(file)
    val expected =
      IvyRepository("ivyRepo", new URL("https://repo1.maven.org"), "[orgPath]", "[orgPath]",
        mavenCompatible = false,
        skipConsistencyCheck = false,
        descriptorOptional = true,
        bootOnly = false)
    repos.size shouldBe 1
    repos(0) shouldBe expected
  }

  it should "parse complex ivy repository definition" in {
    val file = """[repositories]
                 |  ivyRepo: https://repo1.maven.org, [orgPath], [artPath], descriptorOptional, skipConsistencyCheck""".stripMargin
    val repos = RepositoriesParser(file)
    val expected =
      IvyRepository("ivyRepo", new URL("https://repo1.maven.org"), "[orgPath]", "[artPath]",
        mavenCompatible = false,
        skipConsistencyCheck = true,
        descriptorOptional = true,
        bootOnly = false)
    repos.size shouldBe 1
    repos(0) shouldBe expected
  }

  it should "parse multiple repositories defined together" in {
    val file = """[repositories]
                 |  local
                 |  ivyRepo: https://repo1.maven.org, [orgPath], [artPath], descriptorOptional, skipConsistencyCheck
                 |  mavenRepo: https://repo1.maven.org""".stripMargin
    val expected0 = PredefinedRepository(xsbti.Predefined.Local)
    val expected1 =
      IvyRepository("ivyRepo", new URL("https://repo1.maven.org"), "[orgPath]", "[artPath]",
        mavenCompatible = false,
        skipConsistencyCheck = true,
        descriptorOptional = true,
        bootOnly = false)
    val expected2 = MavenRepository("mavenRepo", new URL("https://repo1.maven.org"))

    val repos = RepositoriesParser(file)
    repos.size shouldBe 3
    repos(0) shouldBe expected0
    repos(1) shouldBe expected1
    repos(2) shouldBe expected2
  }

}
