
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test
import scala.annotation.tailrec

object ParallelTest {
	val nbConcurrentTests = new AtomicInteger(0)
	val maxConcurrentTests = new AtomicInteger(0)

	private def updateMaxConcurrentTests(currentMax: Int, newMax: Int) : Boolean = {
		if( maxConcurrentTests.compareAndSet(currentMax, newMax) ) {
			val f = new File("max-concurrent-tests_" + newMax)
			f.createNewFile
			true
		} else {
			false
		}
	}

	@tailrec
	def execute(f : => Unit): Unit = {
		val nb = nbConcurrentTests.incrementAndGet()
		val max = maxConcurrentTests.get()
		if( nb <= max || updateMaxConcurrentTests(max, nb)) {
			f
			nbConcurrentTests.getAndDecrement
		} else {
			nbConcurrentTests.getAndDecrement
			execute(f)
		}
	}
}

class Test1 {
	@Test
	def slow(): Unit = ParallelTest.execute { Thread.sleep(1000) }
}

class Test2 {
	@Test
	def slow(): Unit = ParallelTest.execute { Thread.sleep(1000) }
}

class Test3 {
	@Test
	def slow(): Unit = ParallelTest.execute { Thread.sleep(1000) }
}

class Test4 {
	@Test
	def slow(): Unit = ParallelTest.execute { Thread.sleep(1000) }
}
