// Put in src/test/scala
object Foo {
  // From main slf4j-api JAR
  val logger = org.slf4j.LoggerFactory.getLogger("Foo")
  // From test slf4j-api JAR
  val bp = new org.slf4j.helpers.BogoPerf
}

