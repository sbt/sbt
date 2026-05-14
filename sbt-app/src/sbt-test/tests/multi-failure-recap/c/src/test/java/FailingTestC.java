import org.junit.Test;
import static org.junit.Assert.fail;

public class FailingTestC {
    @Test public void failure() { fail("intentional failure C"); }
}
