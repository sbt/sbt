import org.scalatest._
import java.util.Date

class Test1 extends FlatSpec {
  it should "work" in {
	val start = Counter.add(13)
  	println(s"Starting test 1 ($start)...")

  	Thread.sleep(2000L)

	val end = Counter.get
  	println(s"Test 1 done ($end)")

	assert(end == start, s"Expected Counter to stay at $start, but it changed to $end")
  }
}