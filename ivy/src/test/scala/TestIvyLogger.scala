package xsbt

import xsbti.TestLogger

class TestIvyLogger extends TestLogger with IvyLogger
object TestIvyLogger
{
	def apply[T](f: TestIvyLogger => T): T = TestLogger(new TestIvyLogger)(f)
}