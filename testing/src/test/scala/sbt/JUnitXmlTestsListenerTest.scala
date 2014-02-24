package sbt

import org.backuity.matchete.{XmlMatchers, JunitMatchers}
import org.junit.Test

class JUnitXmlTestsListenerTest extends JunitMatchers with XmlMatchers {

	@Test
	def sysoutCDataShouldBeEscaped() {
		val listener = new JUnitXmlTestsListener("/", ConsoleLogger())
		val event = new JUnitEvent(1234L, "org.the.ATest")
		val xml = listener.toXml("a test", SuiteReport(Seq(TestReport("<![CDATA[ESCAPE ]]> ME! AND ]]> ME!!", event))))

		xml \ "testcase" must containExactly(
			a("'a test' test-case") { case tc =>
				tc must haveAttribute("classname", equalTo("a test"))
				tc must haveAttribute("name", equalTo("ATest"))
				(tc \ "system-out").toString must_== "<system-out><![CDATA[<![CDATA[ESCAPE ]]]]><![CDATA[> ME! AND ]]]]><![CDATA[> ME!!]]></system-out>"
		})
	}

}
