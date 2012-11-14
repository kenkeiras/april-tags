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

        public synchronized void stage(VxObject ... vxp)
        {
            for (VxObject o :  vxp)
                objs.add(o);
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
            HashSet<VxResource> resources = new HashSet();
            VxCodeOutputStream codes = new VxCodeOutputStream();

            MatrixStack ms = new MatrixStack();
            ms.loadIdentity();
            for (VxObject vxo : cobjs) {
                vxo.appendTo(resources, codes, ms);
            }

            // Notify the Layer we need to be rem
            vxs.update(name, resources, codes);
        }
    }

}