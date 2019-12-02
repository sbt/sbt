import complete.DefaultParsers._
import complete.Parser
import sbt.Def.Initialize

val keep = taskKey[Int]("")
val checkKeep = inputKey[Unit]("")

keep := (getPrevious(keep) map { case None => 3; case Some(x) => x + 1 } keepAs keep).value
checkKeep := inputCheck((ctx, s) => Space ~> str(getFromContext(keep, ctx, s))).evaluated

val foo = AttributeKey[Int]("foo")
val transform = taskKey[StateTransform]("")
transform := {
  keep.value
  StateTransform(_.put(foo, 1))
}

def inputCheck[T](f: (ScopedKey[_], State) => Parser[T]): Initialize[InputTask[Unit]] =
  InputTask(resolvedScoped(ctx => (s: State) => f(ctx, s)))(dummyTask)

val checkTransform = taskKey[Unit]("")
checkTransform := {
  assert(state.value.get(foo).contains(1))
}

def dummyTask: Any => Initialize[Task[Unit]] =
  (_: Any) =>
    maxErrors map { _ =>
      ()
    }
def str(o: Option[Int]) = o match { case None => "blue"; case Some(i) => i.toString }
