### Server output flushing no longer disables its own coalescing

The server batches a task's stdout/stderr to the client about once every 20ms to
reduce terminal flicker. Ordering buffered output ahead of a control-plane message
used to shut that timer down, so subsequent output was flushed per write instead
of batched for the rest of the connection. The timer now stays alive.
