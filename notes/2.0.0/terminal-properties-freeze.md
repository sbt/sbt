### One client can no longer freeze the sbt server for every client

An attached client that answered the server's terminal-properties query with a
malformed or error response, or slower than five seconds, left the channel's
terminal permanently uninitialized: threads that render prompts and progress,
including the command loop and the thread handling Ctrl-C, blocked on it
forever, freezing the server for every connected client until the offending
client disconnected. Such responses now fall back to default terminal
properties, unanswered queries expire, and the waits are bounded by the query
in flight.
