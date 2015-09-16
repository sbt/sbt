import java.rmi.RMISecurityException;

public class Test {
    public NotFound foo() { return 5; }

    public String warning() {
        throw new RMISecurityException("O NOES");
    }
}