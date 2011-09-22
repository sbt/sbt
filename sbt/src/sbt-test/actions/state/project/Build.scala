import sbt._
import Keys._
import complete.Parser
import complete.DefaultParsers._
import sbinary.DefaultProtocol._
import Project.Initialize

object MyBuild extends Build
{
	val keep = TaskKey[Int]("keep")
	val persisted = TaskKey[Int]("persist")
	val checkKeep = InputKey[Unit]("check-keep")
	val checkPersisted = InputKey[Unit]("check-persist")
	
	val updateDemo = TaskKey[Int]("demo")
	val check = InputKey[Unit]("check")
	val sample = AttributeKey[Int]("demo-key")

	def updateDemoInit = state map { s => (s get sample getOrElse 9) + 1 }
	
	lazy val root = Project("root", file(".")) settings(
		updateDemo <<= updateDemoInit updateState demoState,
		check <<= checkInit,
		inMemorySetting,
		persistedSetting,
		inMemoryCheck,
		persistedCheck
	)
	def demoState(s: State, i: Int): State = s put (sample, i + 1)

	def checkInit: Initialize[InputTask[Unit]] = InputTask( (_: State) => token(Space ~> IntBasic) ~ token(Space ~> IntBasic).?) { key =>
		(key, updateDemo, state) map { case ((curExpected, prevExpected), value, s) =>
			val prev = s get sample
			assert(value == curExpected, "Expected current value to be " + curExpected + ", got " + value)
			assert(prev == prevExpected, "Expected previous value to be " + prevExpected + ", got " + prev)
		}
	}

	def inMemorySetting = keep <<= getPrevious(keep) map { case None => 3; case Some(x) => x + 1} keepAs(keep)
	def persistedSetting = persisted <<= loadPrevious(persisted) map { case None => 17; case Some(x) => x + 1} storeAs(keep)

	def inMemoryCheck = checkKeep <<= inputCheck( (ctx, s) => Space ~> str(getFromContext(keep, ctx, s)) )
	def persistedCheck = checkPersisted <<= inputCheck( (ctx, s) => Space ~> str(loadFromContext(persisted, ctx, s)) )

	def inputCheck[T](f: (ScopedKey[_], State) => Parser[T]): Initialize[InputTask[Unit]] =
		InputTask( resolvedScoped(ctx => (s: State) => f(ctx, s)) )( dummyTask )

	def dummyTask = (key: Any) => maxErrors map { _ => () }
	def str(o: Option[Int]) = o match { case None => "blue"; case Some(i) => i.toString }
}