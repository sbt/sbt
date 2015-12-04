package macros

object Client extends App {
  assert(Provider.dummyMacro == Helper.foo)
}