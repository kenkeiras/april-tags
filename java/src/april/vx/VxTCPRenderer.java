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

    public VxTCPRenderer(String url)
    {
        if (!url.startsWith("tcp://"))
            throw new IllegalArgumentException("VxLocalRenderer only accepts tcp:// urls");

        url = url.substring("tcp://".length());
        System.out.println("TCP URL >"+url+"<");

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


        try {
            Socket sock = new Socket(host, port);


            ins = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
            outs = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
        } catch (IOException e) {
            System.out.println("ERR: Ex: "+e);
            System.exit(1);
        }

        // Setup a FIFO queue for incoming resource additions, code
        // updates, or deallocations to preserve order on other side
    }


    //*** Methods for all VxRenderers ***//
    public void add_resources(HashSet<VxResource> resources)
    {
        System.out.println("add_resources");
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
        try {
            LCMDataOutputStream lcm_out = new LCMDataOutputStream();
            lcm_resources.encode(lcm_out);

            synchronized (outs) {
                outs.writeInt(VxTCPServer.VX_TCP_ADD_RESOURCES);
                outs.writeInt(lcm_out.size());
                outs.write(lcm_out.getBuffer(), 0, lcm_out.size());
            }
        } catch(IOException e){
            System.out.println("Ex: "+e);
        }
    }

    public  void update_codes(String buffer_name, VxCodeOutputStream codes)
    {
        lcmvx_render_codes_t lcodes = new lcmvx_render_codes_t();
        lcodes.buffer_name = buffer_name;
        lcodes.buf = codes.getBuffer();
        lcodes.buflen = codes.size();

        try {
            LCMDataOutputStream lcm_out = new LCMDataOutputStream();
            lcodes.encode(lcm_out);

            synchronized (outs) {
                outs.writeInt(VxTCPServer.VX_TCP_CODES);
                outs.writeInt(lcm_out.size());
                outs.write(lcm_out.getBuffer(), 0, lcm_out.size());
            }
        } catch(IOException e) {
            System.out.println("Ex: "+e);
        }
    }


    public void remove_resources(HashSet<VxResource> resources)
    {

        lcmvx_dealloc_t dealloc = new lcmvx_dealloc_t();
        dealloc.nguids = resources.size();
        int  i = 0;
        for (VxResource vx : resources)
            dealloc.guids[i++] = vx.id;

        try {
            LCMDataOutputStream lcm_out = new LCMDataOutputStream();
            dealloc.encode(lcm_out);

            synchronized (outs) {
                outs.writeInt(VxTCPServer.VX_TCP_ADD_RESOURCES);
                outs.writeInt(lcm_out.size());
                outs.write(lcm_out.getBuffer(), 0, lcm_out.size());
            }
        } catch(IOException e){
            System.out.println("Ex: "+e);
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
        } catch(IOException e){}
        System.out.println("WRN: Failed to read size over TCP");
        return dim;
    }

}