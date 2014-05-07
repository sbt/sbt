package sbt

import java.io.File
import org.specs2._
import mutable.Specification

object MakePomTest extends Specification {
  val mp = new MakePom(ConsoleLogger())
  import mp.{ makeDependencyVersion => v }
  "MakePom makeDependencyVersion" should {
    "Handle .+ in versions" in {
      v("1.+") must_== "[1,2)"
      v("1.2.3.4.+") must_== "[1.2.3.4,1.2.3.5)"
      v("12.31.42.+") must_== "[12.31.42,12.31.43)"
    }
    /* TODO - do we care about this case?
         * 1+ --> [1,2),[10,20),[100,200),[1000,2000),[10000,20000),[100000,200000)
         */
    "Handle ]* bracket in version ranges" in {
      v("]1,3]") must_== "(1,3]"
      v("]1.1,1.3]") must_== "(1.1,1.3]"
    }
    "Handle *[ bracket in version ranges" in {
      v("[1,3[") must_== "[1,3)"
      v("[1.1,1.3[") must_== "[1.1,1.3)"
    }
  }
}
