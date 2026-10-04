scalaVersion := "3.9.0"
Test / fork := true
libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test

Test / testListeners += new TestsListener:
  def doInit(): Unit = ()
  def startGroup(name: String): Unit = ()
  def testEvent(event: TestEvent): Unit = Thread.sleep(50)
  def endGroup(name: String, t: Throwable): Unit = ()
  def endGroup(name: String, result: TestResult): Unit = ()
  def doComplete(finalResult: TestResult): Unit = ()
