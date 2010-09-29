package april.sim;

import java.util.*;

import april.jmat.*;

public class CompoundShape implements Shape
{
    ArrayList<Object> ops = new ArrayList<Object>();

    public CompoundShape(Object ... os)
    {
        add(os);
    }

    public void add(double M[][])
    {
        ops.add(LinAlg.copy(M));
    }

    public void add(Object ... os)
    {
        int i = 0;

        while (i < os.length) {
            if (os[i] == null) {
                i++;
                continue;
            }

            ops.add(os[i]);
            i++;
        }
    }
}
