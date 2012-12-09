package april.vx;

import lcm.lcm.*;

import java.io.*;
import java.util.*;
import java.net.*;
import april.lcmtypes.*;

// Handels remote rendering
public class VxTCPRenderer extends VxRenderer
{
    DataInputStream ins;
    DataOutputStream outs;

    VxResourceManager manager;

    public VxTCPRenderer(String url)
    {
        if (!url.startsWith("tcp://"))
            throw new IllegalArgumentException("VxLocalRenderer only accepts tcp:// urls");

        url = url.substring("tcp://".length());
        int argidx = url.indexOf("?");
        if (argidx >= 0) {
            String arg = url.substring(argidx+1);
            url = url.substring(0, argidx);

            String params[] = arg.split("&");
            for (String param : params) {
                String keyval[] = param.split("=");
            }
        }

        // bind a connection with specified address
        String url_parts[] = url.split(":");
        String host = url_parts[0];
        int port = Integer.parseInt(url_parts[1]);
        try {
            Socket sock = new Socket(host, port);
            ins = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
            outs = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
            // ins = new DataInputStream(sock.getInputStream());
            // outs = new DataOutputStream(sock.getOutputStream());
        } catch (IOException e) {
            System.out.println("ERR: Ex: "+e);
            System.exit(1);
        }

        // Setup a FIFO queue for incoming resource additions, code
        // updates, or deallocations to preserve order on other side


        // manage resources so we don't use unnecessary bandwidth
        manager = new VxResourceManager(this);
    }


    //*** Methods for all VxRenderers ***//

    public void update_resources_managed(int worldID, String name, HashSet<VxResource> resources)
    {
        manager.update_resources_managed(worldID, name, resources);
    }

    public void add_resources_direct(HashSet<VxResource> resources)
    {
        lcmvx_resource_list_t lcm_resources = new lcmvx_resource_list_t();
        lcm_resources.nresources = resources.size();
        lcm_resources.resources = new lcmvx_resource_t[lcm_resources.nresources];
        int idx = 0;
        for (VxResource vr : resources) {
            lcmvx_resource_t lvr = new lcmvx_resource_t();

            lvr.id   = vr.id;
            lvr.type = vr.type;
            lvr.count = vr.count;
            lvr.fieldwidth = vr.fieldwidth;
            lvr.len = lvr.count * lvr.fieldwidth;
            lvr.res = VxUtil.copyByteArray(vr.res); // Big endian

            lcm_resources.resources[idx++] = lvr;
        }
        writeLCM(VxTCPServer.VX_TCP_ADD_RESOURCES, lcm_resources);
    }

    public void update_buffer(int worldID, String buffer_name, int drawOrder, VxCodeOutputStream codes)
    {
        lcmvx_render_codes_t lcodes = new lcmvx_render_codes_t();
        lcodes.worldID = worldID;
        lcodes.draw_order = drawOrder;
        lcodes.buffer_name = buffer_name;
        lcodes.buf = codes.getBuffer();
        lcodes.buflen = codes.size();

        writeLCM(VxTCPServer.VX_TCP_BUFFER_UPDATE, lcodes);
    }

    public void update_layer(int layerID, int worldID, int drawOrder, float viewport_rel[])
    {
        assert(viewport_rel.length == 4);
        try{
            synchronized (outs) {
                outs.writeInt(VxTCPServer.VX_TCP_LAYER_UPDATE);
                outs.writeInt(4+4+4+4*4);
                outs.writeInt(layerID);
                outs.writeInt(worldID);
                outs.writeInt(drawOrder);
                outs.writeFloat(viewport_rel[0]);
                outs.writeFloat(viewport_rel[1]);
                outs.writeFloat(viewport_rel[2]);
                outs.writeFloat(viewport_rel[3]);

                outs.flush();
            }
        } catch(IOException e){
            System.out.println("Ex: "+e); e.printStackTrace();
        }
    }

    public void remove_resources_direct(HashSet<VxResource> resources)
    {
        lcmvx_dealloc_t dealloc = new lcmvx_dealloc_t();
        dealloc.nguids = resources.size();
        dealloc.guids = new long[dealloc.nguids];
        int  i = 0;
        for (VxResource vx : resources)
            dealloc.guids[i++] = vx.id;

        writeLCM(VxTCPServer.VX_TCP_DEALLOC_RESOURCES, dealloc);
    }

    private void writeLCM(int code, LCMEncodable msg)
    {
        try {
            LCMDataOutputStream lcm_out = new LCMDataOutputStream();
            msg.encode(lcm_out);

            // VxTCPServer.printHex(lcm_out.toByteArray());


            // System.out.printf("code %d len %d\n",code,lcm_out.size());

            synchronized (outs) {
                outs.writeInt(code);
                outs.writeInt(lcm_out.size());
                outs.write(lcm_out.getBuffer(), 0, lcm_out.size());
                outs.flush();
            }
        } catch(IOException e){
            System.out.println("Ex: "+e); e.printStackTrace();
        }
    }

    // Fast for a local implementation
    public int[] get_canvas_size()
    {
        int dim[] = {0,0};

        try {
            synchronized(outs) {
                outs.writeInt(VxTCPServer.VX_TCP_REQUEST_SIZE);
                outs.flush();
            }

            synchronized(ins) {
                int op = ins.readInt();
                if (op == VxTCPServer.VX_TCP_CANVAS_SIZE) {
                    dim[0] = ins.readInt();
                    dim[1] = ins.readInt();
                }
            }
        } catch(IOException e){
            System.out.println("Ex: "+e);
        }
        return dim;
    }

}