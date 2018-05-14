/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.{ File, IOException, PrintWriter, StringWriter }
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Hashtable

import scala.collection.mutable.ListBuffer
import scala.xml.{ Elem, Node => XNode, XML }
import testing.{
  Event => TEvent,
  NestedTestSelector,
  Status => TStatus,
  OptionalThrowable,
  TestSelector
}
import sbt.protocol.testing.TestResult

/**
 * A tests listener that outputs the results it receives in junit xml
 * report format.
 * @param outputDir path to the dir in which a folder with results is generated
 */
class JUnitXmlTestsListener(val outputDir: String) extends TestsListener {

  /**Current hostname so we know which machine executed the tests*/
  val hostname =
    try InetAddress.getLocalHost.getHostName
    catch {
      case _: IOException => "localhost"
    }

  /**The dir in which we put all result files. Is equal to the given dir + "/test-reports"*/
  val targetDir = new File(outputDir + "/test-reports/")

  /**all system properties as XML*/
  val properties =
    <properties>
      {
        // create a clone, defending against [[ConcurrentModificationException]]
        val clonedProperties = System.getProperties.clone.asInstanceOf[Hashtable[AnyRef, AnyRef]]
        val iter = clonedProperties.entrySet.iterator
        val props: ListBuffer[XNode] = new ListBuffer()
        while (iter.hasNext) {
          val next = iter.next
          props += <property name={ next.getKey.toString } value={ next.getValue.toString }/>
        }
        props
      }
    </properties>

  /**
   * Gathers data for one Test Suite. We map test groups to TestSuites.
   * Each TestSuite gets its own output file.
   */
  class TestSuite(val name: String, timestamp: LocalDateTime) {
    def this(name: String) = this(name, LocalDateTime.now())

    val events: ListBuffer[TEvent] = new ListBuffer()

    /**Adds one test result to this suite.*/
    def addEvent(e: TEvent) = events += e

    /** Returns the number of tests of each state for the specified. */
    def count(status: TStatus) = events.count(_.status == status)

    /**
     * Stops the time measuring and emits the XML for
     * All tests collected so far.
     */
    def stop(): Elem = {
      val duration = events.map(_.duration()).sum

      val (errors, failures, tests) = (count(TStatus.Error), count(TStatus.Failure), events.size)

      /** Junit XML reports don't differentiate between ignored, skipped or pending tests */
      val ignoredSkippedPending = count(TStatus.Ignored) + count(TStatus.Skipped) + count(
        TStatus.Pending
      )

      val result =
        <testsuite hostname={ hostname } name={ name } tests={ tests + "" } errors={ errors + "" } failures={ failures + "" } skipped={ ignoredSkippedPending + "" } time={ (duration / 1000.0).toString } timestamp={formatISO8601DateTime(timestamp)}>
                     { properties }
                     {
                       for (e <- events) yield <testcase classname={ name } name={
                         e.selector match {
                           case selector: TestSelector => selector.testName.split('.').last
                           case nested: NestedTestSelector => nested.suiteId().split('.').last + "." + nested.testName()
                           case other => s"(It is not a test it is a ${other.getClass.getCanonicalName})"
                         }
                       } time={ (e.duration() / 1000.0).toString }>
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
                                                     case TStatus.Error if (e.throwable.isDefined)=> <error message={ e.throwable.get.getMessage } type={ e.throwable.get.getClass.getName }>{ trace }</error>
                                                     case TStatus.Error=> <error message={ "No Exception or message provided" }/>
                                                     case TStatus.Failure if (e.throwable.isDefined)=> <failure message={ e.throwable.get.getMessage } type={ e.throwable.get.getClass.getName }>{ trace }</failure>
                                                     case TStatus.Failure=> <failure message={ "No Exception or message provided" }/>
                                                     case TStatus.Ignored | TStatus.Skipped | TStatus.Pending=> <skipped/>
                                                     case _    => {}
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

  /**The currently running test suite*/
  private val testSuite = new InheritableThreadLocal[Option[TestSuite]] {
    override def initialValue(): Option[TestSuite] = None
  }

  private def withTestSuite[T](f: TestSuite => T) =
    testSuite.get().map(f).getOrElse(sys.error("no test suite"))

  /**Creates the output Dir*/
  override def doInit() = {
    val _ = targetDir.mkdirs()
  }

  /**
   * Starts a new, initially empty Suite with the given name.
   */
  override def startGroup(name: String): Unit = testSuite.set(Some(new TestSuite(name)))

  /**
   * Adds all details for the given even to the current suite.
   */
  override def testEvent(event: TestEvent): Unit = for (e <- event.detail) {
    withTestSuite(_.addEvent(e))
  }

  /**
   * called for each class or equivalent grouping
   *  We map one group to one Testsuite, so for each Group
   *  we create an XML which implements the [[https://github.com/windyroad/JUnit-Schema/blob/master/JUnit.xsd JUnit xml
   *  spec]], and looks like this:
   *
   *  <?xml version="1.0" encoding="UTF-8" ?>
   *  <testsuite skipped="w" errors="x" failures="y" tests="z" hostname="example.com" name="eu.henkelmann.bla.SomeTest" time="0.23">
   *       <properties>
   *           <property name="os.name" value="Linux" />
   *           ...
   *       </properties>
   *       <testcase classname="eu.henkelmann.bla.SomeTest" name="testFooWorks" time="0.0" >
   *           <error message="the foo did not work" type="java.lang.NullPointerException">... stack ...</error>
   *       </testcase>
   *       <testcase classname="eu.henkelmann.bla.SomeTest" name="testBarThrowsException" time="0.0" />
   *       <testcase classname="eu.henkelmann.bla.SomeTest" name="testBaz" time="0.0">
   *           <failure message="the baz was no bar" type="junit.framework.AssertionFailedError">...stack...</failure>
   *        </testcase>
   *       <system-out><![CDATA[]]></system-out>
   *       <system-err><![CDATA[]]></system-err>
   *  </testsuite>
   */
  override def endGroup(name: String, t: Throwable) = {
    // create our own event to record the error
    val event = new TEvent {
      def fullyQualifiedName = name
      //def description =
      //"Throwable escaped the test run of '%s'".format(name)
      def duration = -1
      def status = TStatus.Error
      def fingerprint = null
      def selector = null
      def throwable = new OptionalThrowable(t)
    }
    withTestSuite(_.addEvent(event))
    writeSuite()
  }

  /**
   * Ends the current suite, wraps up the result and writes it to an XML file
   *  in the output folder that is named after the suite.
   */
  override def endGroup(name: String, result: TestResult) = {
    writeSuite()
  }

  // Here we normalize the name to ensure that it's a nicer filename, rather than
  // contort the user into not using spaces.
  private[this] def normalizeName(s: String) = s.replaceAll("""\s+""", "-")

  /**
   * Format the date, without milliseconds or the timezone, per the JUnit spec.
   */
  private[this] def formatISO8601DateTime(d: LocalDateTime): String =
    d.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

  private def writeSuite() = {
    val file = new File(targetDir, s"${normalizeName(withTestSuite(_.name))}.xml").getAbsolutePath
    // TODO would be nice to have a logger and log this with level debug
    // System.err.println("Writing JUnit XML test report: " + file)
    XML.save(file, withTestSuite(_.stop()), "UTF-8", true, null)
    testSuite.remove()
  }

  /**Does nothing, as we write each file after a suite is done.*/
  override def doComplete(finalResult: TestResult): Unit = {}

  /**Returns None*/
  override def contentLogger(test: TestDefinition): Option[ContentLogger] = None
}
