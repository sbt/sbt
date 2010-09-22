package lib

object Test
{
	def other = Class.forName("lib.OtherTest")
	def otherThread = Class.forName("lib.OtherTest2", true, Thread.currentThread.getContextClassLoader)
}

class OtherTest
class OtherTest2