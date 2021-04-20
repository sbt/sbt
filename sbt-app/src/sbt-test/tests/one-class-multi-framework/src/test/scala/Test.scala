import org.junit.runner.RunWith
import org.specs2._

@RunWith(classOf[org.specs2.runner.JUnitRunner]) 
class B extends Specification
{
  // sequential=true is necessary to get junit in the call stack
  // otherwise, junit calls specs, which then runs tests on separate threads
  def is = args(sequential=true) ^ s2"""

   This should
    fail if 'succeed' file is missing   $succeedNeeded
    not run via JUnit                   $noJUnit
                                       """

  def succeedNeeded = {
    val f = new java.io.File("succeed")
    f.exists must_== true
  }
  def noJUnit = {
    println("Trace: " + RunBy.trace.mkString("\n\t", "\n\t", ""))
    RunBy.junit must_== false
  }
}

object RunBy
{
  def trace = (new Exception).getStackTrace.map(_.getClassName)
  def junit = trace.exists(_.contains("org.junit"))
}
