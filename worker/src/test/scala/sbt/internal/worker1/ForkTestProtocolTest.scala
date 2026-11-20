package sbt.internal.worker1

import java.util.ArrayList
import org.scalasbt.shadedgson.com.google.gson.JsonParser
import sbt.internal.worker1.ForkTestMain.{
  AnnotatedFingerscan,
  ForkEvent,
  ForkEventsInfo,
  SubclassFingerscan
}
import sbt.testing.{ AnnotatedFingerprint, Event, OptionalThrowable, Status, TestSelector }

object ForkTestProtocolTest extends verify.BasicTestSuite:
  test("forked-test request preserves fingerprints, selectors and runner arguments"):
    val gson = WorkerMain.mkGson()
    val request = JsonParser.parseString(
      """{
        |  "jvm": true, "ansiCodesSupported": true, "parallel": false, "parallelism": 2,
        |  "jvmRunInfo": {"args": [], "classpath": [], "mainClass": "suite.Main", "connectInput": false},
        |  "taskDefs": [
        |    {"_fullyQualifiedName": "suite.One", "_explicitlySpecified": true,
        |     "_fingerprint": {"type": "SubclassFingerscan", "isModule": false,
        |                      "superclassName": "suite.Base", "requireNoArgConstructor": true},
        |     "_selectors": [{"type": "TestSelector", "_testName": "one"}]},
        |    {"_fullyQualifiedName": "suite.Two", "_explicitlySpecified": false,
        |     "_fingerprint": {"type": "AnnotatedFingerscan", "isModule": true, "annotationName": "suite.Annotation"},
        |     "_selectors": [{"type": "SuiteSelector"}]}
        |  ],
        |  "testRunners": [{"implClassNames": ["suite.Framework"],
        |                   "mainRunnerArgs": ["local"], "mainRunnerRemoteArgs": ["remote"]}]
        |}""".stripMargin
    )
    val info = gson.fromJson(request.deepCopy(), classOf[TestInfo])
    val subclass = info.taskDefs.get(0).fingerprint().asInstanceOf[SubclassFingerscan]
    val annotated = info.taskDefs.get(1).fingerprint().asInstanceOf[AnnotatedFingerscan]
    Predef.assert(subclass.superclassName() == "suite.Base" && subclass.requireNoArgConstructor())
    Predef.assert(annotated.annotationName() == "suite.Annotation" && annotated.isModule())
    val reencoded = gson.toJsonTree(info, classOf[TestInfo])
    Predef.assert(reencoded == request, s"Expected $request, got $reencoded")

  test("forked-test replies preserve log and event data"):
    val gson = WorkerMain.mkGson()
    val log = TestLogInfo(5000000000L, ForkTags.Warn, "worker warning")
    val decodedLog = gson.fromJson(gson.toJson(log), classOf[TestLogInfo])
    Predef.assert(
      decodedLog.id == log.id && decodedLog.tag == log.tag && decodedLog.message == log.message
    )

    val error = IllegalStateException("test failed")
    val stackFrame = StackTraceElement("suite.Example", "test", "Example.scala", 7)
    error.setStackTrace(Array(stackFrame))
    val sent = ForkEvent(new Event:
      def fullyQualifiedName() = "suite.Example"
      def fingerprint() = new AnnotatedFingerprint:
        def isModule() = false
        def annotationName() = "suite.Annotation"
      def selector() = TestSelector("test")
      def status() = Status.Failure
      def throwable() = OptionalThrowable(error)
      def duration() = 42L)
    val events = new ArrayList[ForkEvent]()
    events.add(sent)
    val reply = gson.toJsonTree(ForkEventsInfo(log.id, "suite.Example", events)).getAsJsonObject()
    val eventJson = reply.getAsJsonArray("events").get(0).getAsJsonObject()
    Predef.assert(
      eventJson.getAsJsonObject("fingerprint").get("type").getAsString() == "AnnotatedFingerscan"
    )
    Predef.assert(eventJson.getAsJsonObject("selector").get("type").getAsString() == "TestSelector")

    val event = gson.fromJson(reply, classOf[ForkEventsInfo]).events.get(0)
    Predef.assert(
      event.fullyQualifiedName() == sent.fullyQualifiedName() && event.duration() == 42L
    )
    Predef.assert(event.status() == Status.Failure && event.selector() == TestSelector("test"))
    Predef.assert(
      event.fingerprint().asInstanceOf[AnnotatedFingerscan].annotationName() == "suite.Annotation"
    )
    val decodedError = event.throwable().get()
    Predef.assert(
      decodedError.getMessage == s"${classOf[PersistedException].getName}: ${sent.throwable().get().getMessage}"
    )
    Predef.assert(decodedError.getStackTrace.toList == List(stackFrame))

  test("forked-test payloads retain defaults for missing and null fields"):
    val gson = WorkerMain.mkGson()
    val request = gson.fromJson("""{"jvm":null,"taskDefs":null}""", classOf[TestInfo])
    Predef.assert(
      !request.jvm && !request.parallel && request.taskDefs == null && request.testRunners == null
    )
    val log = gson.fromJson("""{"id":null,"message":null}""", classOf[TestLogInfo])
    Predef.assert(log.id == 0L && log.tag == null && log.message == null)
end ForkTestProtocolTest
