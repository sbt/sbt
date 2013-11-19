package sbt

import java.io.{StringWriter, PrintWriter, File}
import java.net.InetAddress
import scala.collection.mutable.ListBuffer
import scala.util.DynamicVariable
import scala.xml.{Elem, Node, XML}
import testing.{Event => TEvent, Status => TStatus, OptionalThrowable, TestSelector}

/**
 * A tests listener that outputs the results it receives in junit xml
 * report format.
 * @param outputDir path to the dir in which a folder with results is generated
 */
class JUnitXmlTestsListener(val outputDir:String) extends TestsListener
{
		/**Current hostname so we know which machine executed the tests*/
    val hostname = InetAddress.getLocalHost.getHostName
    /**The dir in which we put all result files. Is equal to the given dir + "/test-reports"*/
    val targetDir = new File(outputDir + "/test-reports/")

    /**all system properties as XML*/
    val properties =
        <properties> {
            val iter = System.getProperties.entrySet.iterator
            val props:ListBuffer[Node] = new ListBuffer()
            while (iter.hasNext) {
                val next = iter.next
                props += <property name={next.getKey.toString} value={next.getValue.toString} />
            }
            props
        }
        </properties>

    /** Gathers data for one Test Suite. We map test groups to TestSuites.
     * Each TestSuite gets its own output file.
     */
    class TestSuite(val name:String) {
        val events:ListBuffer[TEvent] = new ListBuffer()

        /**Adds one test result to this suite.*/
        def addEvent(e:TEvent) = events += e

        /** Returns the number of tests of each state for the specified. */
        def count(status: TStatus) = events.count(_.status == status)

        /** Stops the time measuring and emits the XML for
         * All tests collected so far.
         */
        def stop():Elem = {
            val duration  = events.map(_.duration()).sum

            val (errors, failures, tests) = (count(TStatus.Error), count(TStatus.Failure), events.size)

            val result = <testsuite hostname={hostname} name={name}
                           tests={tests + ""} errors={errors + ""} failures={failures + ""}
                           time={(duration/1000.0).toString} >
                {properties}
                {
                    for (e <- events) yield
                    <testcase classname={name}
                              name={
                                e.selector match {
                                  case selector: TestSelector => selector.testName.split('.').last
                                  case _ => "(It is not a test)"
                                }
                              }
                              time={(e.duration() / 1000.0).toString}> {
                        var trace:String = if (e.throwable.isDefined) {
                            val stringWriter = new StringWriter()
                            val writer = new PrintWriter(stringWriter)
                            e.throwable.get.printStackTrace(writer)
                            writer.flush()
                            stringWriter.toString
                        }
                        else {
                            ""
                        }
                        e.status match {
                            case TStatus.Error   if (e.throwable.isDefined) => <error message={e.throwable.get.getMessage} type={e.throwable.get.getClass.getName}>{trace}</error>
                            case TStatus.Error                              => <error message={"No Exception or message provided"} />
                            case TStatus.Failure if (e.throwable.isDefined) => <failure message={e.throwable.get.getMessage} type={e.throwable.get.getClass.getName}>{trace}</failure>
                            case TStatus.Failure                            => <failure message={"No Exception or message provided"} />
                            case TStatus.Skipped                            => <skipped />
                            case _               => {}
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
    var testSuite = new DynamicVariable(null: TestSuite)

    /**Creates the output Dir*/
    override def doInit() = {targetDir.mkdirs()}

    /** Starts a new, initially empty Suite with the given name.
     */
    override def startGroup(name: String) {testSuite.value_=(new TestSuite(name))}

    /** Adds all details for the given even to the current suite.
     */
    override def testEvent(event: TestEvent): Unit = for (e <- event.detail) {testSuite.value.addEvent(e)}

    /** called for each class or equivalent grouping
     *  We map one group to one Testsuite, so for each Group
     *  we create an XML like this:
     *  <?xml version="1.0" encoding="UTF-8" ?>
     *  <testsuite errors="x" failures="y" tests="z" hostname="example.com" name="eu.henkelmann.bla.SomeTest" time="0.23">
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
            def fullyQualifiedName= name
            //def description =
              //"Throwable escaped the test run of '%s'".format(name)
              def duration = -1
            def status  = TStatus.Error
            def fingerprint = null
            def selector = null
            def throwable = new OptionalThrowable(t)
        }
        testSuite.value.addEvent(event)
        writeSuite()
    }

    /** Ends the current suite, wraps up the result and writes it to an XML file
     *  in the output folder that is named after the suite.
     */
    override def endGroup(name: String, result: TestResult.Value) = {
        writeSuite()
    }

    private def writeSuite() = {
      val file = new File(targetDir, testSuite.value.name + ".xml").getAbsolutePath
      // TODO would be nice to have a logger and log this with level debug
      // System.err.println("Writing JUnit XML test report: " + file)
      XML.save (file, testSuite.value.stop(), "UTF-8", true, null)
    }

    /**Does nothing, as we write each file after a suite is done.*/
    override def doComplete(finalResult: TestResult.Value): Unit = {}

    /**Returns None*/
    override def contentLogger(test: TestDefinition): Option[ContentLogger] = None
}
