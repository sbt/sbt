object Main extends App {

  try this.synchronized(this.wait)
  catch { case _: InterruptedException => }

}
