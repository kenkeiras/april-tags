package april.vx;

import java.io.*;
import java.util.*;
import java.net.*;
import april.lcmtypes.*;

// Handels remote rendering
public class VxTCPRenderer extends VxRenderer
{
    DataInputStream ins;
    DataOutputStream outs;

    public VxTCPRenderer(String url) throws IOException
    {
        if (!url.startsWith("tcp://"))
            throw new IllegalArgumentException("VxLocalRenderer only accepts tcp:// urls");

        // XXX don't make a gtk_remote renderer, even though we probably could


        // bind a connection with specified address


        int argidx = url.indexOf("?");
        if (argidx >= 0) {
            String arg = url.substring(argidx+1);
            url = url.substring(0, argidx);

            String params[] = arg.split("&");
            for (String param : params) {
                String keyval[] = param.split("=");
            }
        }

        String url_parts[] = url.split(":");
        String host = url_parts[0];
        int port = Integer.parseInt(url_parts[1]);


        Socket sock = new Socket(host, port);


        ins = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
        outs = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
        // Setup a FIFO queue for incoming resource additions, code
        // updates, or deallocations to preserve order on other side
    }


    //*** Methods for all VxRenderers ***//
    public void add_resources(HashSet<VxResource> resources)
    {
        lcmvx_resource_list_t lcm_resources = new lcmvx_resource_list_t();
        lcm_resources.nresources = resources.size();
        int idx = 0;
        for (VxResource vr : resources) {
            lcmvx_resource_t lvr = new lcmvx_resource_t();

            lvr.id   = vr.id;
            lvr.type = vr.type;
            lvr.count = vr.count;
            lvr.fieldwidth = vr.fieldwidth;
            lvr.len = lvr.count * lvr.fieldwidth;
            lvr.res = VxUtil.copyByteArray(vr.res);

            lcm_resources.resources[idx++] = lvr;
        }
        LCMDataOutputStream lcm_out = new LCMDataOutputStream();
        lcm_resources.encode(lcm_out);

        synchronized (outs) {
            outs.write(VxTCPServer.VX_TCP_ADD_RESOURCES);
            outs.write(lcm_out.size());
            outs.write(lcm_out.getBuffer(), 0, lcm_out.size());
        }
    }

    public  void update_codes(String buffer_name, VxCodeOutputStream codes)
    {
    }
    public void remove_resources(HashSet<VxResource> resources)
    {

    }

    // Fast for a local implementation
    public int[] get_canvas_size()
    {
        return new int[]{0,0}; //XXX
    }

}