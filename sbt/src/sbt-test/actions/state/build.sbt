import complete.Parser
import complete.DefaultParsers._
import sbinary.DefaultProtocol._
import Def.Initialize
import sjsonnew.BasicJsonProtocol._

val keep = taskKey[Int]("")
val persist = taskKey[Int]("")
val checkKeep = inputKey[Unit]("")
val checkPersist = inputKey[Unit]("")

val updateDemo = taskKey[Int]("")
val check = inputKey[Unit]("")
val sample = AttributeKey[Int]("demo-key")

def updateDemoInit = state map { s => (s get sample getOrElse 9) + 1 }

lazy val root = (project in file(".")).
  settings(
    updateDemo := (updateDemoInit updateState demoState).value,
    check := checkInit.evaluated,
    inMemorySetting,
    persistedSetting,
    inMemoryCheck,
    persistedCheck
  )

def demoState(s: State, i: Int): State = s put (sample, i + 1)

def checkInit: Initialize[InputTask[Unit]] = Def inputTask {
  val key = (token(Space ~> IntBasic) ~ token(Space ~> IntBasic).?).parsed
  val (curExpected, prevExpected) = key
  val value = updateDemo.value
  val prev = state.value get sample
  assert(value == curExpected, s"Expected current value to be $curExpected, got $value")
  assert(prev == prevExpected, s"Expected previous value to be $prevExpected, got $prev")
}

def  inMemorySetting = keep    :=  (getPrevious(keep)    map { case None =>  3; case Some(x) => x + 1}  keepAs(keep)).value
def persistedSetting = persist := (loadPrevious(persist) map { case None => 17; case Some(x) => x + 1} storeAs(persist)).value

def  inMemoryCheck = checkKeep    := (inputCheck( (ctx, s) => Space ~> str( getFromContext(   keep, ctx, s)) )).evaluated
def persistedCheck = checkPersist := (inputCheck( (ctx, s) => Space ~> str(loadFromContext(persist, ctx, s)) )).evaluated

def inputCheck[T](f: (ScopedKey[_], State) => Parser[T]): Initialize[InputTask[Unit]] =
  InputTask( resolvedScoped(ctx => (s: State) => f(ctx, s)) )( dummyTask )

def dummyTask = (key: Any) => maxErrors map { _ => () }
def str(o: Option[Int]) = o match { case None => "blue"; case Some(i) => i.toString }
