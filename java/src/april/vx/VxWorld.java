package april.vx;
import java.util.*;

public class VxWorld
{

    VxServer vxs;
    ArrayList<Buffer> buffers = new ArrayList();

    public class Buffer
    {
        ArrayList<VxObject> objs = new ArrayList();

        String name;

        public synchronized void add(VxObject vxp)
        {
            objs.add(vxp);
        }

        boolean enabled()
        {
            return true;
        }

        public void commit()
        {
            ArrayList<VxObject> cobjs = null;
            synchronized(this)
            {
                cobjs = objs;
                objs = new ArrayList();
            }

            // Opcodify all the objects in this list, s
            VxObjOpcodes voo = new VxObjOpcodes();

            for (VxObject vxo : cobjs) {
                vxo.appendTo(voo);
            }

            // Notify the Layer we need to be rem
            vxs.update(name, voo);
        }
    }

}