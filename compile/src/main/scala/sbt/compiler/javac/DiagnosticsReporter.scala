package sbt.compiler.javac

import java.io.File
import javax.tools.{ Diagnostic, DiagnosticListener, JavaFileObject }

import sbt.Logger
import xsbti.{ Maybe, Reporter, Severity }
import javax.tools.Diagnostic.NOPOS

/**
  * A diagnostics listener that feeds all messages into the given reporter.
  * @param reporter
  */
final class DiagnosticsReporter(reporter: Reporter) extends DiagnosticListener[JavaFileObject] {
  val END_OF_LINE_MATCHER = "(\r\n)|[\r]|[\n]"
  val EOL = System.getProperty("line.separator")

  private[this] var errorEncountered = false
  def hasErrors: Boolean = errorEncountered

  private def fixedDiagnosticMessage(d: Diagnostic[_ <: JavaFileObject]): String = {
    def getRawMessage = d.getMessage(null)
    def fixWarnOrErrorMessage = {
      val tmp = getRawMessage
      // we fragment off the line/source/type report from the message.
      // NOTE - End of line handling may be off.
      val lines: Seq[String] =
        tmp.split(END_OF_LINE_MATCHER) match {
          case Array(head, tail @ _*) =>
            val newHead = head.split(":").last
            newHead +: tail
          case Array(head) =>
            head.split(":").last :: Nil
          case Array() => Seq.empty[String]
        }
      lines.mkString(EOL)
    }
    d.getKind match {
      case Diagnostic.Kind.ERROR | Diagnostic.Kind.WARNING | Diagnostic.Kind.MANDATORY_WARNING =>
        fixWarnOrErrorMessage
      case _ => getRawMessage
    }
  }
  private def fixSource[T <: JavaFileObject](source: T): Option[String] =
    try Option(source).map(_.toUri.normalize).map(new File(_)).map(_.getAbsolutePath)
    catch {
      case t: IllegalArgumentException =>
        // Oracle JDK6 has a super dumb notion of what a URI is.  In fact, it's not even a legimitate URL, but a dump
        // of the filename in a "I hope this works to toString it" kind of way.  This appears to work in practice
        // but we may need to re-evaluate.
        Option(source).map(_.toUri.toString)
    }
  override def report(d: Diagnostic[_ <: JavaFileObject]): Unit = {
    val severity =
      d.getKind match {
        case Diagnostic.Kind.ERROR                                       => Severity.Error
        case Diagnostic.Kind.WARNING | Diagnostic.Kind.MANDATORY_WARNING => Severity.Warn
        case _                                                           => Severity.Info
      }
    val msg = fixedDiagnosticMessage(d)
    val pos: xsbti.Position =
      new xsbti.Position {
        // https://docs.oracle.com/javase/7/docs/api/javax/tools/Diagnostic.html
        // Negative values (except NOPOS) and 0 are not valid line or column numbers.
        private[this] def checkNoPos(n: Long): Option[Long] =
          n match {
            case NOPOS       => None
            case x if x <= 0 => sys.error(s"Invalid position: $x")
            case x           => Option(x)
          }

        override val line: Maybe[Integer] = Logger.o2m(checkNoPos(d.getLineNumber) map { x =>
          new Integer(x.toInt)
        })
        def startPosition: Option[Long] = checkNoPos(d.getStartPosition)
        def endPosition: Option[Long] = checkNoPos(d.getEndPosition)
        override val offset: Maybe[Integer] = Logger.o2m(checkNoPos(d.getPosition) map { x =>
          new Integer(x.toInt)
        })
        override def lineContent: String = {
          def getDiagnosticLine: Option[String] =
            try {
              // See com.sun.tools.javac.api.ClientCodeWrapper.DiagnosticSourceUnwrapper
              val diagnostic = d.getClass.getField("d").get(d)
              // See com.sun.tools.javac.util.JCDiagnostic#getDiagnosticSource
              val getDiagnosticSourceMethod = diagnostic.getClass.getDeclaredMethod("getDiagnosticSource")
              val getPositionMethod = diagnostic.getClass.getDeclaredMethod("getPosition")
              (
                Option(getDiagnosticSourceMethod.invoke(diagnostic)),
                Option(getPositionMethod.invoke(diagnostic))
              ) match {
                case (Some(diagnosticSource), Some(position: java.lang.Long)) =>
                  // See com.sun.tools.javac.util.DiagnosticSource
                  val getLineMethod = diagnosticSource.getClass.getMethod("getLine", Integer.TYPE)
                  Option(getLineMethod.invoke(diagnosticSource, new Integer(position.intValue())))
                    .map(_.toString)
                case _ => None
              }
            } catch {
              // TODO - catch ReflectiveOperationException once sbt is migrated to JDK7
              case ignored: Throwable => None
            }

          def getExpression: String =
            Option(d.getSource) match {
              case Some(source: JavaFileObject) =>
                (Option(source.getCharContent(true)), startPosition, endPosition) match {
                  case (Some(cc), Some(start), Some(end)) => cc.subSequence(start.toInt, end.toInt).toString
                  case _                                  => ""
                }
              case _ => ""
            }

          getDiagnosticLine.getOrElse(getExpression)
        }
        private val sourceUri = fixSource(d.getSource)
        override val sourcePath = Logger.o2m(sourceUri)
        override val sourceFile = Logger.o2m(sourceUri.map(new File(_)))
        override val pointer = Logger.o2m(Option.empty[Integer])
        override val pointerSpace = Logger.o2m(Option.empty[String])
        override def toString =
          if (sourceUri.isDefined) s"${sourceUri.get}:${if (line.isDefined) line.get else -1}"
          else ""
      }
    if (severity == Severity.Error) errorEncountered = true
    reporter.log(pos, msg, severity)
  }
}
