package april.vx;

import java.util.*;
import java.io.*;
import java.net.*;

import april.util.*;
import april.lcmtypes.*;

import lcm.lcm.LCMDataInputStream;

import javax.swing.*;

public class VxTCPServer extends Thread
{

    // The server listens for codes on the input stream -- it expects either
    // a set of resources, or a set of codes. Marshalling is handled by LCM data types
    public static final int VX_TCP_ADD_RESOURCES = 0x1,VX_TCP_BUFFER_UPDATE = 0x2, VX_TCP_DEALLOC_RESOURCES = 0x3,
        VX_TCP_REQUEST_SIZE = 0x4, VX_TCP_CANVAS_SIZE = 0x5, VX_TCP_LAYER_UPDATE = 0x6;

    final int port;

    VxRenderer rend;

    // Synchronize on VxTCPServer.this
    ArrayList queue = new ArrayList();

    VxTCPServer(VxRenderer rend, int port)
    {
        this.port = port;
        this.rend = rend;

        new ServerThread().start();
    }

    class ServerThread extends Thread
    {
        public void run()
        {
            try {
                ServerSocket serverSock = new ServerSocket(port);

                while (true) {
                    Socket sock = serverSock.accept();
                    new ClientThread(sock).start();
                }
            } catch (IOException ex) {
                System.out.println("ex: "+ex);
                System.exit(1);
            }
        }
    }

    private void process_buffer(VxCodeInputStream cins)//lcmvx_render_codes_t lcm_codes)
    {
        // Convert and send off
        int worldID = cins.readInt();
        String buffer_name = cins.readStringZ();
        int drawOrder = cins.readInt();
        int clen = cins.readInt();
        byte buf[] = new byte[clen];
        cins.readFully(buf);
        VxCodeOutputStream vout = new VxCodeOutputStream(buf);

        rend.update_buffer(worldID, buffer_name, drawOrder, vout);
    }

    private void process_dealloc(VxCodeInputStream cins)
    {
        // Convert and send off
        HashSet<VxResource> dealloc = new HashSet();
        int ct = cins.readInt();
        for (int i = 0; i < ct; i++)
            dealloc.add(new VxResource(0, null, 0, 0, cins.readLong()));

        rend.remove_resources_direct(dealloc);
    }

    private void process_resources(VxCodeInputStream cins) //lcmvx_resource_list_t lcm_resources)
    {
        HashSet<VxResource> resources = new HashSet();
        int nresources = cins.readInt();
        for (int i = 0; i < nresources; i++) {
            long id = cins.readLong();
            int type = cins.readInt();
            int count = cins.readInt();
            int fieldwidth = cins.readInt();
            byte resb[] = new byte[count*fieldwidth];
            cins.readFully(resb);
            Object res = VxUtil.copyToType(resb, type);
            VxResource vr = new VxResource(type, res, count, fieldwidth, id);
            resources.add(vr);
        }
        rend.add_resources_direct(resources);
    }

    private void process_layer(VxCodeInputStream cins)
    {
        rend.update_layer(cins.readInt(), cins.readInt(), cins.readInt(),
                          new float[]{cins.readFloat(), cins.readFloat(), cins.readFloat(), cins.readFloat()});
    }

    class ClientThread extends Thread
    {
        Socket sock;

        public ClientThread(Socket sock)
        {
            this.sock = sock;
        }

        public void run()
        {
            try {
                DataInputStream ins = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
                DataOutputStream outs = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));

                // Read data from the TCP connection, and guarantee that it is processed FIFO by the rendering thread
                //
                while (true) {
                    int code = ins.readInt();
                    // System.out.printf("Read code: %d %x\n",code, code);

                    if (code == VX_TCP_REQUEST_SIZE) {
                        int dim[] = rend.get_canvas_size();
                        outs.writeInt(VX_TCP_CANVAS_SIZE);
                        outs.writeInt(dim[0]);
                        outs.writeInt(dim[1]);
                        outs.flush();
                        continue;
                    }

                    int len = ins.readInt();
                    byte buf[] = new byte[len];
                    ins.readFully(buf);

                    VxCodeInputStream cins = new VxCodeInputStream(buf);
                    switch(code) {
                        case VX_TCP_ADD_RESOURCES:
                            process_resources(cins);
                            break;
                        case VX_TCP_DEALLOC_RESOURCES:
                            process_dealloc(cins);
                            break;
                        case VX_TCP_BUFFER_UPDATE:
                            process_buffer(cins);
                            break;
                        case VX_TCP_LAYER_UPDATE:
                            process_layer(cins);
                            break;
                        default:
                            System.out.printf("WRN: Unsupported OP code! 0x%x\n",code);
                    }
                    assert(cins.available() == 0);
                }

            } catch (EOFException ex) {
                System.out.println("Client disconnected");
            } catch (IOException ex) {
                System.out.println("ex: "+ex); ex.printStackTrace();
            }

            try {
                sock.close();
            } catch(IOException ex) {
                System.out.println("ex: "+ex);
            }
        }
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addInt('p',"port",15151,"Which port to listen on");
        opts.addString('u',"url","java://?width=480&height=480","Which VxRenderer to use");

        if (!opts.parse(args) || opts.getBoolean("help") || opts.getExtraArgs().size() > 0) {
            opts.doHelp();
            return;
        }

        VxLocalRenderer vxlr = new VxLocalRenderer(opts.getString("url"));
        // XXX Replace this with a Canvas and give  place to display
        new VxTCPServer(vxlr,
                        opts.getInt("port"));

        int canvas_size[] = vxlr.get_canvas_size();


        JFrame jf = new JFrame();
        VxCanvas vc = new VxCanvas(vxlr);
        jf.add(vc);
        jf.setSize(canvas_size[0], canvas_size[1]);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        while(true) {
            TimeUtil.sleep(1000);
        }
    }

    // Debug
    // Convert a byte array to a hex string
    public static void printHex(byte a[])
    {
        for (int i = 0; i < a.length; i++) {
            System.out.printf("%02X ", a[i]);
            if ((i + 1) % 16 == 0)
                System.out.printf("\n");
            else if ((i +1) % 8 == 0)
                System.out.printf(" ");
        }
        System.out.println();
    }
}