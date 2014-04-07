import org.junit.Test

package a.pkg {
	class OneSecondTest {
		@Test
		def oneSecond() {
			Thread.sleep(1000)
		}
	}
}

package another.pkg {
	class FailingTest {
		@Test
		def failure1_OneSecond() {
			Thread.sleep(1000)
			sys.error("fail1")
		}

		@Test
		def failure2_HalfSecond() {
			Thread.sleep(500)
			sys.error("fail2")
		}
	}

	// junit doesn't blow up when a class fails during construction
	// we'll use scalatest (which does blow up) to make sure
	// the report contains that kind of error
	import org.scalatest.{FunSpec, ShouldMatchers}
	class FailUponConstructionTest extends FunSpec with ShouldMatchers {
		sys.error("failed upon construction")

		describe("5 should") {
			it("always be 5") {
				5 should equal (5)
			}
		}
	}
}

package console.test.pkg {
	// we won't check console output in the report
	// until SBT supports that
	class ConsoleTests {
		@Test
		def sayHello() {
			println("Hello")
			System.out.println("World!")
		}

		@Test
		def multiThreadedHello() {
			val threads = for( i <- 1 to 15 ) yield {
				new Thread("t-" + i) {
					override def run() {
						println("Hello from thread " + i)
					}
				}
			}
			threads.foreach( _.start() )
			threads.foreach( _.join() )
		}
	}
}