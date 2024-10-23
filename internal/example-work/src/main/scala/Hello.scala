package example

@main def main(args: String*): Unit =
  if args.toList == List("boom") then sys.error("boom")
  else println(s"${args.mkString}")
