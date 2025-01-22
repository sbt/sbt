package example

class Hello

object Hello:
  def main(args: Array[String]): Unit =
    if args.toList == List("boom") then sys.error("boom")
    else println(s"${args.mkString}")
end Hello
