package april.vx;
import java.util.*;

public class VxWorld
{

    VxServer vxs;
    HashMap<String, Buffer> bufferMap = new HashMap();

    public VxWorld(VxServer serv)
    {
        this.vxs = serv;
    }

    public Buffer getBuffer(String name)
    {
        Buffer buf = bufferMap.get(name);
        if (buf == null) {
            buf = new Buffer();
            buf.name = name;
            bufferMap.put(name, buf);
        }

        return buf;
    }

    public class Buffer
    {
        ArrayList<VxObject> objs = new ArrayList();

        String name;

        public synchronized void stage(VxObject vxp)
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
            ArrayList<VxResource> resources = new ArrayList();
            VxCodeOutputStream codes = new VxCodeOutputStream();

            for (VxObject vxo : cobjs) {
                vxo.appendTo(resources, codes);
            }

            // Notify the Layer we need to be rem
            vxs.update(name, resources, codes);
        }
    }

}