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
    public static final int VX_TCP_ADD_RESOURCES = 0x1,VX_TCP_CODES = 0x2, VX_TCP_DEALLOC_RESOURCES = 0x3,
        VX_TCP_REQUEST_SIZE = 0x4, VX_TCP_CANVAS_SIZE = 0x5;

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

    private void process_codes(lcmvx_render_codes_t lcm_codes)
    {
        // Convert and send off
        VxCodeOutputStream vout = new VxCodeOutputStream(lcm_codes.buf);
        rend.update_codes(lcm_codes.buffer_name, vout);
    }

    private void process_dealloc(lcmvx_dealloc_t lcm_dealloc)
    {
        // Convert and send off
        HashSet<VxResource> dealloc = new HashSet();
        for (int i = 0; i < lcm_dealloc.nguids; i++)
            dealloc.add(new VxResource(0, null, 0, 0, lcm_dealloc.guids[i]));

        rend.remove_resources(dealloc);
    }

    private void process_resources(lcmvx_resource_list_t lcm_resources)
    {
        HashSet<VxResource> resources = new HashSet();
        for (int i = 0; i < lcm_resources.nresources; i++) {
            lcmvx_resource_t lvr = lcm_resources.resources[i];

            // Returns a correctly parsed array. Transport is big-endian
            Object res = VxUtil.copyToType(lvr.res, lvr.type);
            VxResource vr = new VxResource(lvr.type, res, lvr.count, lvr.fieldwidth, lvr.id);
            resources.add(vr);
        }
        rend.add_resources(resources);
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
                    ins.read(buf);
                    // printHex(buf);
                    // System.out.printf("Read len %d\n",len);

                    switch(code) {
                        case VX_TCP_ADD_RESOURCES:
                            process_resources(new lcmvx_resource_list_t(new LCMDataInputStream(buf)));
                            break;
                        case VX_TCP_DEALLOC_RESOURCES:
                            process_dealloc(new lcmvx_dealloc_t(new LCMDataInputStream(buf)));
                            break;
                        case VX_TCP_CODES:
                            process_codes(new lcmvx_render_codes_t(new LCMDataInputStream(buf)));
                            break;
                    }
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