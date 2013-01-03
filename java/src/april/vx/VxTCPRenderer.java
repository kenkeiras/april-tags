package april.vx;

import java.io.*;
import java.util.*;
import java.net.*;

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
        VxCodeOutputStream ocodes = new VxCodeOutputStream();

        ocodes.writeInt(resources.size());

        for (VxResource vr : resources) {
            ocodes.writeLong(vr.id);
            ocodes.writeInt(vr.type);
            ocodes.writeInt(vr.count);
            ocodes.writeInt(vr.fieldwidth);
            byte res[] = VxUtil.copyByteArray(vr.res); // float,longs, etc are stored as big endian!
            assert(res.length == vr.count * vr.fieldwidth);
            ocodes.write(res, 0, res.length);
        }

        writeVCOS(VxTCPServer.VX_TCP_ADD_RESOURCES, ocodes);
    }

    public void update_buffer(int worldID, String buffer_name, int drawOrder, VxCodeOutputStream codes)
    {
        VxCodeOutputStream ocodes = new VxCodeOutputStream();
        ocodes.writeInt(worldID);
        ocodes.writeStringZ(buffer_name);
        ocodes.writeInt(drawOrder);
        ocodes.writeInt(codes.size());
        ocodes.write(codes.getBuffer(), 0, codes.size());

        writeVCOS(VxTCPServer.VX_TCP_BUFFER_UPDATE, ocodes);
    }

    public void update_layer(int layerID, int worldID, int drawOrder, float viewport_rel[])
    {
        assert(viewport_rel.length == 4);

        VxCodeOutputStream ocodes = new VxCodeOutputStream();

        ocodes.writeInt(layerID);
        ocodes.writeInt(worldID);
        ocodes.writeInt(drawOrder);
        ocodes.writeFloat(viewport_rel[0]);
        ocodes.writeFloat(viewport_rel[1]);
        ocodes.writeFloat(viewport_rel[2]);
        ocodes.writeFloat(viewport_rel[3]);

        writeVCOS(VxTCPServer.VX_TCP_LAYER_UPDATE, ocodes);
    }

    public void remove_resources_direct(HashSet<VxResource> resources)
    {
        VxCodeOutputStream ocodes = new VxCodeOutputStream();

        ocodes.writeInt(resources.size());
        for (VxResource vr : resources)
            ocodes.writeLong(vr.id);

        writeVCOS(VxTCPServer.VX_TCP_DEALLOC_RESOURCES, ocodes);
    }

    // Fast for a local implementation, not in this case though
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

    private void writeVCOS(int code, VxCodeOutputStream codes)
    {
        try {
            synchronized(outs) {
                outs.writeInt(code);
                outs.writeInt(codes.size());
                outs.write(codes.getBuffer(), 0, codes.size());
                outs.flush();
            }
        } catch (IOException e) {
            System.out.println("Ex: "+e); e.printStackTrace();
        }
    }
}
