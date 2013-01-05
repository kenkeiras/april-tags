package april.vx;
import java.util.*;

import java.util.concurrent.atomic.*;

public class VxWorld
{
    static AtomicInteger worldNextID = new AtomicInteger(1);

    // Each world gets a unique ID. Ensures there aren't name collisions on buffer names,
    // and makes it easy for a VxLayer to specify which world to render.
    final int worldID = worldNextID.getAndIncrement();

    ArrayList<VxRenderer> listeners = new ArrayList();
    HashMap<String, Buffer> bufferMap = new HashMap();

    public VxWorld(VxRenderer ... vxrends)
    {
        listeners.addAll(Arrays.asList(vxrends));
    }

    public void addListeners(VxRenderer ... vxrends)
    {
        listeners.addAll(Arrays.asList(vxrends));
    }

    public synchronized Buffer getBuffer(String name)
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
        int drawOrder = 0;

        public synchronized void addBack(VxObject ... vxp)
        {
            for (VxObject o :  vxp)
                objs.add(o);
        }

        boolean enabled()
        {
            return true;
        }

        public void setDrawOrder(int drawOrder)
        {
            this.drawOrder = drawOrder;
        }

        public void swap()
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

            for (VxRenderer vxrend : listeners) {
                // Use the managed path for updating the resources
                vxrend.update_resources_managed(worldID, name, resources);
                // Codes can go straight to the renderer
                vxrend.update_buffer(worldID, name, drawOrder, codes);
            }
        }
    }

}