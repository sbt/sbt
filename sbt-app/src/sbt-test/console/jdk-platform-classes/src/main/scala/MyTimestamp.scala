// Linkage through the defining loader: resolving the java.sql.Timestamp supertype of a
// project-classpath class exercises the classpath loader's parent chain, not a top-down
// REPL request. This is the shape of sbt/sbt#4328's original repro (a JDBC driver
// implementing java.sql.Driver).
class MyTimestamp extends java.sql.Timestamp(0L)
