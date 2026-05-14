import org.junit.Test;
import static org.junit.Assert.fail;

public class FailingTestA {
    @Test public void failure() { fail("intentional failure A"); }
}
