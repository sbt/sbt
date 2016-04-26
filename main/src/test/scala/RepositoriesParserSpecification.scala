package sbt

import org.specs2.Specification

import java.net.URL

/**
 * Tests that we can correctly parse repositories definitions.
 */
object RepositoriesParserSpecification extends Specification {
  import RepositoriesParser._

  def is = s2"""
  This is a specification for the parser for repositories.

  RepositoriesParser should
    Repositories file start with [repositories]     $checkHeader
    Parse the local repository                      $localRepository
    Parse the local maven repository                $localMavenRepository
    Parse Maven Central repository                  $mavenCentralRepository
    Parse simple Maven repository                   $simpleMavenRepository
    Parse `bootOnly` option                         $bootOnlyOption
    Parse `mavenCompatible` option                  $mavenCompatibleOption
    Parse `skipConsistencyCheck` option             $skipConsistencyCheckOption
    Parse `descriptorOptional` option               $descriptorOptionalOption
    Parse complex ivy repository definition         $complexIvyRepository
    Parse multiple repositories defined together    $multipleRepositories
  """

  def checkHeader = {
    val file = """local
                 |maven-central""".stripMargin
    RepositoriesParser(file) should throwA[Exception]
  }

  def localRepository = {
    val file = """[repositories]
                 |  local""".stripMargin
    val repos = RepositoriesParser(file)
    repos should haveSize(1) and
      (repos(0) should beEqualTo(PredefinedRepository(xsbti.Predefined.Local)))
  }

  def localMavenRepository = {
    val file = """[repositories]
                 |  maven-local""".stripMargin
    val repos = RepositoriesParser(file)
    repos should haveSize(1) and
      (repos(0) should beEqualTo(PredefinedRepository(xsbti.Predefined.MavenLocal)))
  }

  def mavenCentralRepository = {
    val file = """[repositories]
                 |  maven-central""".stripMargin
    val repos = RepositoriesParser(file)
    repos should haveSize(1) and
      (repos(0) should beEqualTo(PredefinedRepository(xsbti.Predefined.MavenCentral)))
  }

  def simpleMavenRepository = {
    val file = """[repositories]
                 |  mavenRepo: https://repo1.maven.org""".stripMargin
    val repos = RepositoriesParser(file)
    repos should haveSize(1) and
      (repos(0) should beEqualTo(MavenRepository("mavenRepo", new URL("https://repo1.maven.org"))))
  }

  def bootOnlyOption = {
    val file = """[repositories]
                 |  ivyRepo: https://repo1.maven.org, [orgPath], bootOnly""".stripMargin
    val repos = RepositoriesParser(file)
    val expected =
      IvyRepository("ivyRepo", new URL("https://repo1.maven.org"), "[orgPath]", "[orgPath]",
        mavenCompatible = false,
        skipConsistencyCheck = false,
        descriptorOptional = false,
        bootOnly = true)
    repos should haveSize(1) and
      (repos(0) should beEqualTo(expected))
  }

  def mavenCompatibleOption = {
    val file = """[repositories]
                 |  ivyRepo: https://repo1.maven.org, [orgPath], mavenCompatible""".stripMargin
    val repos = RepositoriesParser(file)
    val expected =
      IvyRepository("ivyRepo", new URL("https://repo1.maven.org"), "[orgPath]", "[orgPath]",
        mavenCompatible = true,
        skipConsistencyCheck = false,
        descriptorOptional = false,
        bootOnly = false)
    repos should haveSize(1) and
      (repos(0) should beEqualTo(expected))
  }

  def skipConsistencyCheckOption = {
    val file = """[repositories]
                 |  ivyRepo: https://repo1.maven.org, [orgPath], skipConsistencyCheck""".stripMargin
    val repos = RepositoriesParser(file)
    val expected =
      IvyRepository("ivyRepo", new URL("https://repo1.maven.org"), "[orgPath]", "[orgPath]",
        mavenCompatible = false,
        skipConsistencyCheck = true,
        descriptorOptional = false,
        bootOnly = false)
    repos should haveSize(1) and
      (repos(0) should beEqualTo(expected))
  }

  def descriptorOptionalOption = {
    val file = """[repositories]
                 |  ivyRepo: https://repo1.maven.org, [orgPath], descriptorOptional""".stripMargin
    val repos = RepositoriesParser(file)
    val expected =
      IvyRepository("ivyRepo", new URL("https://repo1.maven.org"), "[orgPath]", "[orgPath]",
        mavenCompatible = false,
        skipConsistencyCheck = false,
        descriptorOptional = true,
        bootOnly = false)
    repos should haveSize(1) and
      (repos(0) should beEqualTo(expected))
  }

  def complexIvyRepository = {
    val file = """[repositories]
                 |  ivyRepo: https://repo1.maven.org, [orgPath], [artPath], descriptorOptional, skipConsistencyCheck""".stripMargin
    val repos = RepositoriesParser(file)
    val expected =
      IvyRepository("ivyRepo", new URL("https://repo1.maven.org"), "[orgPath]", "[artPath]",
        mavenCompatible = false,
        skipConsistencyCheck = true,
        descriptorOptional = true,
        bootOnly = false)
    repos should haveSize(1) and
      (repos(0) should beEqualTo(expected))
  }

  def multipleRepositories = {
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
    repos should haveSize(3) and
      (repos(0) should beEqualTo(expected0)) and
      (repos(1) should beEqualTo(expected1)) and
      (repos(2) should beEqualTo(expected2))
  }

}
