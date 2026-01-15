import ch.qos.logback.classic.BasicConfigurator
import ch.qos.logback.classic.LoggerContext

object GcMetricsApp {
  def main(args: Array[String]): Unit = {
    BasicConfigurator.configure(new LoggerContext())
  }
}
