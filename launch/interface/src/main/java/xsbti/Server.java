package xsbti;

/** A running server.
 * 
 * A class implementing this must:
 * 
 * 1. Expose an HTTP port that clients can connect to, returned via the uri method.
 * 2. Accept HTTP HEAD requests against the returned URI. These are used as "ping" messages to ensure
 *    a server is still alive, when new clients connect.
 * 3. Create a new thread to execute its service
 * 4. Block the calling thread until the server is shutdown via awaitTermination()
 */
public interface Server {
	/**
	 * @return
	 *    A URI denoting the Port which clients can connect to.  
	 *
	 *    Note: we use a URI so that the server can bind to different IP addresses (even a public one) if desired.
	 *    Note: To verify that a server is "up", the sbt launcher will attempt to connect to
	 *          this URI's address and port with a socket.  If the connection is accepted, the server is assumed to
	 *          be working.
	 */
	public java.net.URI uri();
	/**
	 * This should block the calling thread until the server is shutdown.
     * 
	 * @return
	 *      The result that should occur from the server.
	 *      Can be:
	 *      - xsbti.Exit:  Shutdown this launch
	 *      - xsbti.Reboot:  Restart the server
	 *      
	 *
	 */
	public xsbti.MainResult awaitTermination();
}