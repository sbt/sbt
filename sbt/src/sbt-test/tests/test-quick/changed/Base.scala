import java.io.File

trait Base {
	val marker = new File("marker")
	// Test compilation group change.
	val baz = ""
}
