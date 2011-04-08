/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

import org.scalacheck._
import Gen.choose

object TaskGen
{
	// upper bounds to make the tests finish in reasonable time
	val MaxTasks = 100
	val MaxWorkers = 29
	val MaxJoin = 20
	
	val MaxTasksGen = choose(0, MaxTasks)
	val MaxWorkersGen = choose(1, MaxWorkers)
	val MaxJoinGen = choose(0, MaxJoin)
	val TaskListGen = MaxTasksGen.flatMap(size => Gen.listOfN(size, Arbitrary.arbInt.arbitrary))
	
}