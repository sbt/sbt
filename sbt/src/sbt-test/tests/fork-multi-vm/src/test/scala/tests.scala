
import org.junit.Test

package a.pkg {

	import java.io.File
	import java.util.concurrent.atomic.AtomicInteger

	object Tests {
		// if tests are executed within the same vm, then _noExecutedTests
		// should end up 4
		private val _noExecutedTests = new AtomicInteger(0)

		def execute() {
			val noExecutedTests = _noExecutedTests.incrementAndGet()
			new File("target/" + noExecutedTests).createNewFile()

			// make sure each test is executed in a different vm.
			// If a test was running too fast its vm could pick another test from the test suite queue
			// before the queue gets emptied by the remaining vms.
			Thread.sleep(5000)
		}
	}

	class Test1 {
		@Test def test1() { Tests.execute() }
	}

	class Test2 {
		@Test def test2() { Tests.execute() }
	}

	class Test3 {
		@Test def test3() { Tests.execute() }
	}

	class Test4 {
		@Test def test4() { Tests.execute() }
	}
}