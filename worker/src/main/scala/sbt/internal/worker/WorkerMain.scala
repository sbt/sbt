package sbt.internal.worker

import scala.Console

object WorkerMain:
  def main(args: Array[String]): Unit =
    val originalIn = Console.in
    val originalOut = Console.out
    val dispatch = WorkDispatch(stdout = originalOut.println)
    val line = originalIn.readLine()
    dispatch.request(line)
    ()
end WorkerMain
