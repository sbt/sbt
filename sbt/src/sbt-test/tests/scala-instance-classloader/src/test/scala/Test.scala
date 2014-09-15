package akka.actor

import org.junit._

class BadTest {

  @Test
  def testCpIssue(): Unit = {
    // TODO - This is merely the laziest way to run the test.  What we want to do:
    // * Load something from our own classloader that's INSIDE the scala library
    // * Try to load that same something from the THREAD CONTEXT classloader.
    // * Ensure we can do both, i.e. the second used to be filtered and broken.
    val current = Thread.currentThread.getContextClassLoader
    val mine = this.getClass.getClassLoader
    val system = ActorSystem()
    def evilGetThreadExectionContextName =
      system.asInstanceOf[ActorSystemImpl].internalCallingThreadExecutionContext.getClass.getName
    val expected = "scala.concurrent.Future$InternalCallbackExecutor$"
    Assert.assertEquals("Failed to grab appropriate Akka name", expected, evilGetThreadExectionContextName)
  }
}