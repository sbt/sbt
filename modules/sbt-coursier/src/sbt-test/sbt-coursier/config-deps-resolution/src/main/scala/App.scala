import ch.qos.logback.classic.BasicConfigurator
import ch.qos.logback.classic.LoggerContext

object GcMetricsApp extends App {
  BasicConfigurator.configure(new LoggerContext())
}
