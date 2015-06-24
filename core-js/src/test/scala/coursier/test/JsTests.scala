package coursier
package test

import coursier.core.DefaultFetchMetadata
import coursier.test.compatibility._

import utest._

import scala.concurrent.{Future, Promise}

object JsTests extends TestSuite {

  val tests = TestSuite {
    'promise {
      val p = Promise[Unit]()
      Future(p.success(()))
      p.future
    }

    'get{
      DefaultFetchMetadata.get("http://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.1.3/logback-classic-1.1.3.pom")
        .map(core.compatibility.xmlParse)
        .map{ xml =>
          assert(xml.right.toOption.exists(_.label == "project"))
        }
    }

    'getProj{
      repository.mavenCentral
        .find(Module("ch.qos.logback", "logback-classic"), "1.1.3")
        .map{ proj =>
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
