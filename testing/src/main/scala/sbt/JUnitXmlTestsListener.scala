/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ File, IOException, PrintWriter, StringWriter }
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Hashtable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }

import scala.collection.mutable.ListBuffer
import scala.util.Properties
import scala.xml.{ Elem, Node as XNode, XML }
import testing.{
  Event as TEvent,
  NestedTestSelector,
  Status as TStatus,
  OptionalThrowable,
  TestSelector
}
import util.Logger
import sbt.protocol.testing.TestResult

/**
 * Companion object for JUnitXmlTestsListener that caches the hostname lazily.
 * This ensures hostname resolution only happens once per session and doesn't
 * block sbt startup (only resolves when tests actually run).
 * See https://github.com/sbt/sbt/issues/8601
 */
object JUnitXmlTestsListener {

  /** Cached hostname resolution result with timing info */
  private lazy val hostnameInfo: (String, Long) = {
    val start = System.nanoTime
    val name =
      try InetAddress.getLocalHost.getHostName
      catch {
        case _: IOException => "localhost"
      }
    val elapsed = System.nanoTime - start
    (name, elapsed)
  }

  /** Lazily resolved hostname, cached at object level */
  lazy val hostname: String = hostnameInfo._1

  /** Time taken to resolve hostname in nanoseconds */
  lazy val hostnameResolutionTime: Long = hostnameInfo._2
}

/**
 * A tests listener that outputs the results it receives in junit xml report format.
 * @param targetDir
 *   directory in which test reports are generated
 */
class JUnitXmlTestsListener(val targetDir: File, legacyTestReport: Boolean, logger: Logger)
    extends TestsListener {
  // These constructors are for binary compatibility with older versions of sbt
  // Use old hard-coded behaviour for constructing `targetDir` from `outputDir`
  def this(outputDir: String, legacyTestReport: Boolean, logger: Logger) =
    this(new File(outputDir, "test-reports"), legacyTestReport, logger)
  def this(outputDir: String, logger: Logger) = this(outputDir, false, logger)
  def this(outputDir: String) = this(outputDir, false, null)

  /** Current hostname so we know which machine executed the tests */
  lazy val hostname: String = {
    val name = JUnitXmlTestsListener.hostname
    val elapsed = JUnitXmlTestsListener.hostnameResolutionTime
    if ((NANOSECONDS.toSeconds(elapsed) >= 4) && Properties.isMac && logger != null) {
      logger.warn(
        s"Getting the hostname $name was slow (${elapsed / 1.0e6} ms). " +
          "This is likely because the computer's hostname is not set. You can set the " +
          """hostname with the command: scutil --set HostName "$(scutil --get LocalHostName).local"."""
      )
    }
    name
  }

  /** all system properties as XML */
  val properties: Elem =
    <properties>
      {
      // create a clone, defending against [[ConcurrentModificationException]]
      val clonedProperties = System.getProperties.clone.asInstanceOf[Hashtable[AnyRef, AnyRef]]
      val iter = clonedProperties.entrySet.iterator
      val props: ListBuffer[XNode] = new ListBuffer()
      while (iter.hasNext) {
        val next = iter.next
        props += <property name={next.getKey.toString} value={next.getValue.toString}/>
      }
      props
    }
    </properties>

  /**
   * Gathers data for one Test Suite. We map test groups to TestSuites. Each TestSuite gets its own
   * output file.
   */
  class TestSuite(val name: String, timestamp: LocalDateTime) {
    def this(name: String) = this(name, LocalDateTime.now())

    val events: ListBuffer[TEvent] = new ListBuffer()

    /** Adds one test result to this suite. */
    def addEvent(e: TEvent): ListBuffer[TEvent] = events += e

    /** Returns the number of tests of each state for the specified. */
    def count(status: TStatus) = events.count(_.status == status)

    /**
     * Stops the time measuring and emits the XML for All tests collected so far.
     */
    def stop(): Elem = {
      val duration = events.map(_.duration()).sum

      val (errors, failures, tests) = (count(TStatus.Error), count(TStatus.Failure), events.size)

      // Junit XML reports don't differentiate between ignored, skipped or pending tests
      val ignoredSkippedPending = count(TStatus.Ignored) + count(TStatus.Skipped) + count(
        TStatus.Pending
      )

      // for sbt/junit-interface version 0.11 (in future versions this should be done there)
      val classnameRegex = s"^($name|${name.split('.').last})\\.?".r

      val result =
        <testsuite hostname={hostname} name={name} tests={tests.toString} errors={
          errors.toString
        } failures={
          failures.toString
        } skipped={ignoredSkippedPending.toString} time={(duration / 1000.0).toString} timestamp={
          formatISO8601DateTime(timestamp)
        }>
          {properties}
          {
          for (e <- events)
            yield <testcase classname={
              e.selector match {
                case nested: NestedTestSelector => nested.suiteId()
                case _                          => name
              }
            } name={
              e.selector match {
                case selector: TestSelector =>
                  val matchEnd =
                    classnameRegex.findFirstMatchIn(selector.testName).map(_.end).getOrElse(0)
                  selector.testName.substring(matchEnd)
                case nested: NestedTestSelector => nested.testName()
                case other => s"(It is not a test it is a ${other.getClass.getCanonicalName})"
              }
            } time={(e.duration() / 1000.0).toString}>
                      {
              val trace: String = if (e.throwable.isDefined) {
                val stringWriter = new StringWriter()
                val writer = new PrintWriter(stringWriter)
                e.throwable.get.printStackTrace(writer)
                writer.flush()
                stringWriter.toString
              } else {
                ""
              }
              e.status match {
                case TStatus.Error if (e.throwable.isDefined) =>
                  <error message={e.throwable.get.getMessage} type={
                    e.throwable.get.getClass.getName
                  }>{trace}</error>
                case TStatus.Error =>
                  <error message={"No Exception or message provided"}/>
                case TStatus.Failure if (e.throwable.isDefined) =>
                  <failure message={e.throwable.get.getMessage} type={
                    e.throwable.get.getClass.getName
                  }>{trace}</failure>
                case TStatus.Failure =>
                  <failure message={"No Exception or message provided"}/>
                case TStatus.Ignored | TStatus.Skipped | TStatus.Pending =>
                  <skipped/>
                case _ => {}
              }
            }
                    </testcase>

        }
          <system-out><![CDATA[]]></system-out>
          <system-err><![CDATA[]]></system-err>
        </testsuite>

      result
    }
  }

  /**
   * A mutable cell holding the suite that is currently running on a thread.
   *
   * The cell exists purely so the suite can be released deterministically. `testSuite` below
   * is an [[InheritableThreadLocal]], so every thread created while a suite is running -- for
   * example a pooled worker spawned by an async test framework -- receives a copy of the
   * *reference* to this cell at construction time. `ThreadLocal.remove()` only clears the
   * calling thread's entry, so those inherited copies would otherwise pin the `TestSuite`, its
   * buffered events, and through them the test class loader (and its open jar handles) for the
   * remaining life of the JVM. Clearing the cell severs the reference for the owning thread and
   * every thread that inherited it at once.
   */
  private final class SuiteRef(initial: Option[TestSuite]) {
    private val ref = new AtomicReference(initial)
    def current: Option[TestSuite] = ref.get()
    def clear(): Unit = ref.set(None)
  }

  /** The currently running test suite */
  private val testSuite = new InheritableThreadLocal[SuiteRef] {
    override def initialValue(): SuiteRef = new SuiteRef(None)
  }

  /**
   * Suites started and not yet written, by group name rather than by thread: a forked worker runs a
   * group's classes concurrently and sbt delivers all their events on the one thread reading that
   * connection, so several suites are open per thread.
   */
  private val openSuites = new ConcurrentHashMap[String, TestSuite]()

  /**
   * Groups that have called [[doInit]] and not yet [[doComplete]]. One listener serves every group of
   * a test task and both are per group, so only the last group out may drop what is still open.
   */
  private val openRuns = new AtomicInteger(0)

  /** Creates the output Dir */
  override def doInit(): Unit = {
    openRuns.incrementAndGet()
    val _ = targetDir.mkdirs()
  }

  /**
   * Starts a new, initially empty Suite with the given name.
   */
  override def startGroup(name: String): Unit = {
    val suite = new TestSuite(name)
    openSuites.put(name, suite)
    testSuite.set(new SuiteRef(Some(suite)))
  }

  /**
   * Adds all details for the given even to the current suite.
   *
   * Events that arrive after the suite has been written are dropped. Test frameworks may call
   * the event handler from threads they spawned during the run (see `TestFramework.TestRunner`),
   * and such a thread can report after `writeSuite` has already emitted the XML. Before the
   * suite was released those late events were appended to an already-written suite, so they
   * were discarded in practice; dropping them here keeps that outcome without turning every
   * late event into an error line via `TestFramework.safeForeach`.
   */
  override def testEvent(event: TestEvent): Unit =
    for (e <- event.detail) suiteOf(e) match {
      case Some(suite) => suite.addEvent(e)
      case None        =>
        if (logger != null) {
          logger.debug("ignoring a test event reported after the suite was written")
        } else ()
    }

  /**
   * The open suite an event belongs to: the one it names, since that is the only thing telling two
   * suites on one thread apart, else the thread's own -- for an event named after something other
   * than its group, a nested task's class say, which only that thread can attribute.
   */
  private def suiteOf(e: TEvent): Option[TestSuite] =
    Option(e.fullyQualifiedName)
      .flatMap(name => Option(openSuites.get(name)))
      .orElse(testSuite.get().current)

  /**
   * called for each class or equivalent grouping We map one group to one Testsuite, so for each
   * Group we create
   * [[https://github.com/windyroad/JUnit-Schema/blob/master/JUnit.xsd JUnit XML file]], and looks
   * like this:
   *
   * <?xml version="1.0" encoding="UTF-8" ?> <testsuite skipped="w" errors="x" failures="y"
   * tests="z" hostname="example.com" name="eu.henkelmann.bla.SomeTest" time="0.23"
   * timestamp="2018-01-01T10:00:00"> <properties> <property name="os.name" value="Linux" /> ...
   * </properties> <testcase classname="eu.henkelmann.bla.SomeTest" name="testFooWorks" time="0.0" >
   * <error message="the foo did not work" type="java.lang.NullPointerException">... stack
   * ...</error> </testcase> <testcase classname="eu.henkelmann.bla.SomeTest"
   * name="testBarThrowsException" time="0.0" /> <testcase classname="eu.henkelmann.bla.SomeTest"
   * name="testBaz" time="0.0"> <failure message="the baz was no bar"
   * type="junit.framework.AssertionFailedError">...stack...</failure> </testcase>
   * <system-out><![CDATA[]]></system-out> <system-err><![CDATA[]]></system-err> </testsuite>
   */
  override def endGroup(name: String, t: Throwable): Unit = {
    // create our own event to record the error
    val event: TEvent = new TEvent {
      def fullyQualifiedName = name
      // def description =
      // "Throwable escaped the test run of '%s'".format(name)
      def duration() = -1
      def status = TStatus.Error
      def fingerprint = null
      def selector = null
      def throwable = new OptionalThrowable(t)
    }
    suiteOf(event).foreach(_.addEvent(event))
    writeSuite(name)
  }

  /**
   * Ends the current suite, wraps up the result and writes it to an XML file in the output folder
   * that is named after the suite.
   */
  override def endGroup(name: String, result: TestResult): Unit = {
    writeSuite(name)
  }

  // Here we normalize the name to ensure that it's a nicer filename, rather than
  // contort the user into not using spaces.
  private def normalizeName(s: String) = s.replaceAll("""\s+""", "-")

  /**
   * Format the date, without milliseconds or the timezone, per the JUnit spec.
   */
  private def formatISO8601DateTime(d: LocalDateTime): String =
    d.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

  /**
   * Writes the suite `name` stands for, if it is still open. By name, not by the ending thread: a
   * suite can end on a thread that never started it, since sbt reports the suites a dying worker left
   * open from the thread watching that process. Ending a name twice writes it once.
   */
  private def writeSuite(name: String): Unit = Option(openSuites.remove(name)) match {
    case None =>
      if (logger != null) {
        logger.debug(s"ignoring the end of test suite $name, which is not open")
      }
    case Some(suite) =>
      val file = if (legacyTestReport) {
        new File(targetDir, s"${normalizeName(suite.name)}.xml").getAbsolutePath
      } else {
        new File(targetDir, s"TEST-${normalizeName(suite.name)}.xml").getAbsolutePath
      }
      if (logger != null) {
        logger.debug(s"writing JUnit XML test report: $file")
      }
      XML.save(file, suite.stop(), "UTF-8", xmlDecl = true, null)
      /* Only when this thread's cell is the suite just written -- the other suites open on this
       * thread still need theirs. Order matters: `clear()` releases the suite for this thread
       * *and* for every thread that inherited the cell, which `remove()` cannot reach. `remove()`
       * then drops this thread's own entry. Without the `clear()` the suite -- and through its
       * buffered events the test class loader with its open jar handles -- would stay reachable
       * from pooled worker threads for the life of the JVM.
       */
      val ref = testSuite.get()
      if (ref.current.exists(_ eq suite)) {
        ref.clear()
        testSuite.remove()
      }
  }

  /**
   * Drops any suite left open, which no longer has an end to write it -- but only once the last group
   * is out, since a suite still open belongs to a group still running. See [[openRuns]].
   */
  override def doComplete(finalResult: TestResult): Unit =
    if (openRuns.decrementAndGet() <= 0) openSuites.clear()

  /** Returns None */
  override def contentLogger(test: TestDefinition): Option[ContentLogger] = None
}
