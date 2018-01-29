package coursier.test

import coursier.{Attributes, MavenRepository, Repository}
import coursier.ivy.IvyRepository
import coursier.util.Parse
import coursier.util.Parse.{ModuleParseError, ModuleRequirements}
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

    "jitpack" - {
      val res = Parse.repository("jitpack")
      assert(res.exists(isMavenRepo))
    }

    // Module parsing tests
    "org:name:version" - {
      Parse.moduleVersionConfig("org.apache.avro:avro:1.7.4", ModuleRequirements(), transitive = true, "2.11.11") match {
        case Left(err) => assert(false)
        case Right(dep) =>
          assert(dep.module.organization == "org.apache.avro")
          assert(dep.module.name == "avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == "default(compile)")
          assert(dep.attributes == Attributes())
      }
    }

    "org:name:version:conifg" - {
      Parse.moduleVersionConfig("org.apache.avro:avro:1.7.4:runtime", ModuleRequirements(), transitive = true, "2.11.11") match {
        case Left(err) => assert(false)
        case Right(dep) =>
          assert(dep.module.organization == "org.apache.avro")
          assert(dep.module.name == "avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == "runtime")
          assert(dep.attributes == Attributes())
      }
    }

    "single attr" - {
      Parse.moduleVersionConfig("org.apache.avro:avro:1.7.4:runtime,classifier=tests", ModuleRequirements(), transitive = true, "2.11.11") match {
        case Left(err) => assert(false)
        case Right(dep) =>
          assert(dep.module.organization == "org.apache.avro")
          assert(dep.module.name == "avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == "runtime")
          assert(dep.attributes == Attributes("", "tests"))
      }
    }

    "multiple attrs" - {
      Parse.moduleVersionConfig("org.apache.avro:avro:1.7.4:runtime,classifier=tests,nickname=superman", ModuleRequirements(), transitive = true, "2.11.11") match {
        case Left(err) => assert(false)
        case Right(dep) =>
          assert(dep.module.organization == "org.apache.avro")
          assert(dep.module.name == "avro")
          assert(dep.version == "1.7.4")
          assert(dep.configuration == "runtime")
          assert(dep.attributes == Attributes("", "tests"))
      }
    }

    "single attr with org::name:version" - {
      Parse.moduleVersionConfig("io.get-coursier.scala-native::sandbox_native0.3:0.3.0-coursier-1,attr1=val1", ModuleRequirements(), transitive = true, "2.11.11") match {
        case Left(err) => assert(false)
        case Right(dep) =>
          assert(dep.module.organization == "io.get-coursier.scala-native")
          assert(dep.module.name.contains("sandbox_native0.3")) // use `contains` to be scala version agnostic
          assert(dep.version == "0.3.0-coursier-1")
      }
    }

    "illegal 1" - {
      try {
        Parse.moduleVersionConfig("org.apache.avro:avro,1.7.4:runtime,classifier=tests", ModuleRequirements(), transitive = true, "2.11.11")
        assert(false) // Parsing should fail but succeeded.
      }
      catch {
        case foo: ModuleParseError => assert(foo.getMessage().contains("':' is not allowed in attribute")) // do nothing
        case _: Throwable => assert(false) // Unexpected exception
      }
    }

    "illegal 2" - {
      try {
        Parse.moduleVersionConfig("junit:junit:4.12,attr", ModuleRequirements(), transitive = true, "2.11.11")
        assert(false) // Parsing should fail but succeeded.
      }
      catch {
        case foo: ModuleParseError => assert(foo.getMessage().contains("Failed to parse attribute")) // do nothing
        case _: Throwable => assert(false) // Unexpected exception
      }
    }
  }
}
