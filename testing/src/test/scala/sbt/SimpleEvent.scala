package sbt

import sbt.testing._

class SimpleEvent(val duration: Long, val fullyQualifiedName: String, val fingerprint: Fingerprint,
		val status: Status = Status.Success, error: Option[Throwable] = None) extends Event {

	final val throwable: OptionalThrowable = error match {
		case Some(err) => new OptionalThrowable(err)
		case None => new OptionalThrowable
	}

	val selector: Selector = new TestSelector(fullyQualifiedName)
}

object JUnitAnnotation extends AnnotatedFingerprint {
	override def annotationName(): String = "org.junit.Test"
	override def isModule: Boolean = false
}

class JUnitEvent(duration: Long, fullyQualifiedName: String, status: Status = Status.Success, error: Option[Throwable] = None) extends SimpleEvent(
	duration, fullyQualifiedName, JUnitAnnotation, status, error)