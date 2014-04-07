import org.junit.Test

package a.failing.pkg {

	class FailingTest {
		@Test
		def fail() {
			for( i <- 1 to 10 ) {
				println("This must show up")
				Thread.sleep(10)
			}
			sys.error("fail")
		}
	}

	class SuccessfulTest {
		@Test
		def success1() {
			for( i <- 1 to 10 ) {
				println("This shouldn't show up 1")
				Thread.sleep(10)
			}
		}

		@Test
		def success2() {
			new Thread() {
				override def run() {
					for( i <- 1 to 10 ) {
						println("This shouldn't show up (from thread)")
						Thread.sleep(10)
					}
				}
			}.start()

			for( i <- 1 to 10 ) {
				println("This shouldn't show up 2")
				Thread.sleep(10)
			}
		}
	}
}