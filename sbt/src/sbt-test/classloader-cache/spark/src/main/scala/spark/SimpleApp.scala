import org.apache.spark.sql.SparkSession

object SimpleApp {
  def main(args: Array[String]) {
    val logFile = "log.txt"
    val spark = SparkSession.builder.appName("Simple Application").config("spark.master", "local").getOrCreate()
    try {
      val logData = spark.read.textFile(logFile).cache()
      val numAs = logData.filter(line => line.contains("a")).count()
      val numBs = logData.filter(line => line.contains("b")).count()
      println(s"Lines with a: $numAs, Lines with b: $numBs")
    } finally spark.stop()
  }
}
