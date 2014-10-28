package sbt.compiler.javac

import java.io.File
import javax.tools.{ Diagnostic, JavaFileObject, DiagnosticListener }

import sbt.Logger
import xsbti.{ Severity, Reporter }

/**
 * A diagnostics listener that feeds all messages into the given reporter.
 * @param reporter
 */
final class DiagnosticsReporter(reporter: Reporter) extends DiagnosticListener[JavaFileObject] {

  private def fixedDiagnosticMessage(d: Diagnostic[_ <: JavaFileObject]): String = {
    def getRawMessage = d.getMessage(null)
    def fixWarnOrErrorMessage = {
      val tmp = getRawMessage
      // we fragment off the line/source/type report from the message.
      val lines: Seq[String] =
        tmp.split("[\r\n]") match {
          case Array(head, tail @ _*) =>
            val newHead = head.split(":").last
            newHead +: tail
          case Array(head) =>
            head.split(":").last :: Nil
          case Array() => Seq.empty[String]
        }
      // TODO - Real EOL
      lines.mkString("\n")
    }
    d.getKind match {
      case Diagnostic.Kind.ERROR | Diagnostic.Kind.WARNING | Diagnostic.Kind.MANDATORY_WARNING => fixWarnOrErrorMessage
      case _ => getRawMessage
    }
  }
  override def report(d: Diagnostic[_ <: JavaFileObject]) {
    val severity =
      d.getKind match {
        case Diagnostic.Kind.ERROR => Severity.Error
        case Diagnostic.Kind.WARNING | Diagnostic.Kind.MANDATORY_WARNING => Severity.Warn
        case _ => Severity.Info
      }
    val msg = fixedDiagnosticMessage(d)
    val pos: xsbti.Position =
      new xsbti.Position {
        override val line =
          Logger.o2m(if (d.getLineNumber == -1) None
          else Option(new Integer(d.getLineNumber.toInt)))
        override def lineContent = {
          // TODO - Is this pulling error correctly? Is null an ok return value?
          Option(d.getSource).
            flatMap(s => Option(s.getCharContent(true))).
            map(_.subSequence(d.getStartPosition.intValue, d.getEndPosition.intValue).toString).
            getOrElse("")
        }
        override val offset = Logger.o2m(Option(Integer.valueOf(d.getPosition.toInt)))
        private val sourceUri = Option(d.getSource).map(_.toUri.toString)
        override val sourcePath = Logger.o2m(sourceUri)
        override val sourceFile = Logger.o2m(sourceUri.map(new File(_)))
        override val pointer = Logger.o2m(Option.empty[Integer])
        override val pointerSpace = Logger.o2m(Option.empty[String])
        override def toString = s"${d.getSource}:${d.getLineNumber}"
      }
    reporter.log(pos, msg, severity)
  }
}
