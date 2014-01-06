package xsbti;

/** The main entry point for a launched service.  This allows applciations
 * to instantiate server instances.
 */
public interface ServerMain {
  /**
   * This method should launch one or more thread(s) which run the service.  After the service has
   * been started, it should return the port/URI it is listening for connections on.
   * 
   * @param configuration
   *          The configuration used to launch this service.
   * @return
   *    A running server.
   */
  public Server start(AppConfiguration configuration);
}