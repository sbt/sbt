package sbt

import java.io.{StringWriter, PrintWriter, File}
import java.net.InetAddress
import scala.collection.mutable.ListBuffer
import scala.util.DynamicVariable
import scala.xml.{Elem, Node, XML}
import testing.{Event => TEvent, Status => TStatus, OptionalThrowable, TestSelector}

/**
 * A tests listener that outputs the results it receives in junit xml
 * report format. XSD can be found here: http://windyroad.com.au/dl/Open%20Source/JUnit.xsd (note: we might want to download this into the project?)
 * @param outputDir path to the dir in which a folder with results is generated
 */
class JUnitXmlTestsListener(val outputDir:String, logger: Logger) extends TestsListener
{
		/**Current hostname so we know which machine executed the tests*/
    val hostname = InetAddress.getLocalHost.getHostName
    /**The dir in which we put all result files. Is equal to the given dir + "/test-reports"*/
    val targetDir = new File(outputDir + "/test-reports/")

    /**all system properties as XML*/
    def properties =
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

		private def cdata(content: String) = scala.xml.Unparsed("<![CDATA[%s]]>".format(content))

		private def stackTraceToString(t: Throwable) : String = {
			val stringWriter = new StringWriter()
			val writer = new PrintWriter(stringWriter)
			t.printStackTrace(writer)
			writer.flush()
			stringWriter.toString
		}

    def toXml(name:String, suite: SuiteReport) : Elem = {
				val errors = if( suite.result.errorCount == 0 && suite.errorCause.isDefined) 1 else suite.result.errorCount

				<testsuite hostname={hostname} name={name}
											 tests={suite.detail.size + ""} errors={errors + ""} failures={suite.result.failureCount + ""}
											 time={(suite.duration / 1000.0).toString} >
						{properties}
						{
								for (e <- suite.detail) yield
								<testcase classname={name}
													name={
														e.detail.selector match {
															case selector: TestSelector => selector.testName.split('.').last
															case _ => "(It is not a test)"
														}
													}
													time={(e.detail.duration() / 1000.0).toString}> {
										val trace: String = if (e.detail.throwable.isDefined) {
											stackTraceToString(e.detail.throwable.get)
										}
										else {
											""
										}
										e.detail.status match {
												case TStatus.Error   if (e.detail.throwable.isDefined) => <error message={e.detail.throwable.get.getMessage} type={e.detail.throwable.get.getClass.getName}>{trace}</error>
												case TStatus.Error                              => <error message={"No Exception or message provided"} />
												case TStatus.Failure if (e.detail.throwable.isDefined) => <failure message={e.detail.throwable.get.getMessage} type={e.detail.throwable.get.getClass.getName}>{trace}</failure>
												case TStatus.Failure                            => <failure message={"No Exception or message provided"} />
												case TStatus.Skipped                            => <skipped />
												case _               => {}
												}
								}
									<system-out>{cdata(e.stdout)}</system-out>
								</testcase>

						}
						<system-out><![CDATA[]]></system-out>
						<system-err>{cdata(suite.errorCause.map(stackTraceToString).getOrElse(""))}</system-err>
						</testsuite>

    }

    override def doInit() = {targetDir.mkdirs()}

    /** Ends the current suite, wraps up the result and writes it to an XML file
     *  in the output folder that is named after the suite.
     */
    override def endSuite(name: String, suite: SuiteReport) = {
        writeSuite(name, suite)
    }

    private def writeSuite(name: String, suite: SuiteReport) = {
      val file = new File(targetDir, name + ".xml").getAbsolutePath
      logger.debug("Writing JUnit XML test report: " + file)
      XML.save (file, toXml(name, suite), "UTF-8", true, null)
    }
}
