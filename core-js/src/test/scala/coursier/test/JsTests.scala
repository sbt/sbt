package coursier
package test

import coursier.core.{Repository, MavenRepository}
import coursier.test.compatibility._

import utest._

import scala.concurrent.{Future, Promise}

object JsTests extends TestSuite {

  val tests = TestSuite {
    'promise{
      val p = Promise[Unit]()
      Future(p.success(()))
      p.future
    }

    'get{
      MavenRepository.get("http://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.1.3/logback-classic-1.1.3.pom")
        .map(core.compatibility.xmlParse)
        .map{ xml =>
          assert(xml.right.toOption.exists(_.label == "project"))
        }
    }

    'getProj{
      Repository.mavenCentral
        .find(Module("ch.qos.logback", "logback-classic"), "1.1.3")
        .map{case (_, proj) =>
          assert(proj.parent == Some(Module("ch.qos.logback", "logback-parent"), "1.1.3"))
        }
        .run
        .runF
        .map{ res =>
          assert(res.isRight)
        }
    }
  }

}
