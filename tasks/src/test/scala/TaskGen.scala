import org.scalacheck._
import Gen.choose

object TaskGen
{
	// upper bounds to make the tests finish in reasonable time
	val MaxTasks = 10000
	val MaxWorkers = 257
	val MaxJoin = 100
	
	val MaxTasksGen = choose(0, MaxTasks)
	val MaxWorkersGen = choose(1, MaxWorkers)
	val MaxJoinGen = choose(0, MaxJoin)
	val TaskListGen = MaxTasksGen.flatMap(size => Gen.listOfN(size, Arbitrary.arbInt.arbitrary))
	
}