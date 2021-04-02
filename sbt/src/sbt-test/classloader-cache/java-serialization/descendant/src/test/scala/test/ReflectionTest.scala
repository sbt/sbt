package test

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import org.scalatest._

class ReflectionTest extends FlatSpec {
  val procs = 2
  val initLatch = new CountDownLatch(procs)
  val loader = this.getClass.getClassLoader
  val latch = new CountDownLatch(procs)
  (1 to procs).foreach { i =>
    new Thread() {
      setDaemon(true)
      start()
      override def run(): Unit = {
        initLatch.countDown()
        initLatch.await(5, TimeUnit.SECONDS)
        val className = if (i % 2 == 0) "test.Foo" else "test.Bar"
        loader.loadClass(className)
        val foo = new Foo
        foo.setValue(3)
        val newFoo = reflection.Reflection.roundTrip(foo)
        assert(newFoo == foo)
        assert(System.identityHashCode(newFoo) != System.identityHashCode(foo))
        latch.countDown()
      }
    }
  }
  assert(latch.await(5, TimeUnit.SECONDS))
}

