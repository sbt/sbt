package coursier.test

import coursier.{MavenRepository, Repository}
import coursier.ivy.IvyRepository
import coursier.util.Parse
import utest._

object ParseTests extends TestSuite {

  def isMavenRepo(repo: Repository): Boolean =
    repo match {
      case _: MavenRepository => true
      case _ => false
    }

  def isIvyRepo(repo: Repository): Boolean =
    repo match {
      case _: IvyRepository => true
      case _ => false
    }

  val tests = TestSuite {
    "bintray-ivy:" - {
      val obtained = Parse.repository("bintray-ivy:scalameta/maven")
      assert(obtained.exists(isIvyRepo))
    }
    "bintray:" - {
      val obtained = Parse.repository("bintray:scalameta/maven")
      assert(obtained.exists(isMavenRepo))
    }

    "sbt-plugin:" - {
      val res = Parse.repository("sbt-plugin:releases")
      assert(res.exists(isIvyRepo))
    }

    "typesafe:ivy-" - {
      val res = Parse.repository("typesafe:ivy-releases")
      assert(res.exists(isIvyRepo))
    }
    "typesafe:" - {
      val res = Parse.repository("typesafe:releases")
      assert(res.exists(isMavenRepo))
    }
  }
}
