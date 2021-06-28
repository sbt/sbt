import org.scalatest._
import java.util.Date

class Test2 extends FlatSpec {
  it should "work" in {
   val start = Counter.add(7)
  	println(s"Starting test 2 ($start)...")

  	Thread.sleep(5000L)

   val end = Counter.get
  	println(s"Test 2 done ($end)")

	assert(end == start, s"Expected Counter to stay at $start, but it changed to $end")
  }
}