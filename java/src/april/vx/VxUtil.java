package april.vx;
import java.util.concurrent.atomic.*;

public class VxUtil
{
    static AtomicLong nextId = new AtomicLong(1);

    public static long allocateID()
    {
        return nextId.getAndIncrement();
    }

}