import org.junit.Test

package a.pkg {
	class OneSecondTest {
		@Test
		def oneSecond(): Unit = {
			Thread.sleep(1000)
		}
	}
}

package another.pkg {
	class FailingTest {
		@Test
		def failure1_OneSecond(): Unit = {
			Thread.sleep(1000)
			sys.error("fail1")
		}

		@Test
		def failure2_HalfSecond(): Unit = {
			Thread.sleep(500)
			sys.error("fail2")
		}
	}
}

package console.test.pkg {
	// we won't check console output in the report
	// until SBT supports that
	class ConsoleTests {
		@Test
		def sayHello(): Unit = {
			println("Hello")
			System.out.println("World!")
		}

		@Test
		def multiThreadedHello(): Unit = {
			for( i <- 1 to 5 ) {
				new Thread("t-" + i) {
					override def run(): Unit =
						println("Hello from thread " + i)
				}.start()
			}
		}
	}
}
