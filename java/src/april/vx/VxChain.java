package april.vx;

import java.util.*;

public class VxChain implements VxObject
{

    ArrayList<Object> ops = new ArrayList<Object>();

    public VxChain(Object ... os)
    {
        add(os);
    }

    // this method must be added to disabiguate between a
    // two-dimensional array being interpreted as a varargs call
    // consisting of several one-dimensional doubles.
    public void add(double M[][])
    {
        ops.add(M);
    }

    public void add(Object ... os)
    {
        int i = 0;

        while (i < os.length) {
            if (os[i] == null) {
                i++;
                continue;
            }

            if (os[i] instanceof double[][]) {
                ops.add(os[i]);
                i++;
                continue;
            }

            if (os[i] instanceof VxObject) {
                ops.add((VxObject) os[i]);
                i++;
                continue;
            }

            // unknown type!
            System.out.println("VxChain: Unknown object added to chain: "+os[i]);
            assert(false);
            i++;
        }
    }


    public void appendTo(HashSet<VxResource> resources, VxCodeOutputStream codes, MatrixStack ms)
    {
        ms.pushMatrix();

        for (Object o : ops) {

            if (o instanceof double[][]) {
                ms.multMatrix((double[][]) o);
                continue;
            }

            if (o instanceof VxObject) {
                VxObject vo = (VxObject) o;
                vo.appendTo(resources, codes, ms);
            }
        }
        ms.popMatrix();
    }

}