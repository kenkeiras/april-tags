package april.vx;

import java.util.*;
import java.io.*;
import java.net.*;

import april.util.*;
import april.lcmtypes.*;

import lcm.lcm.LCMDataInputStream;

public class VxTCPServer extends Thread
{

    // The server listens for codes on the input stream -- it expects either
    // a set of resources, or a set of codes. Marshalling is handled by LCM data types
    public static final int VX_TCP_RESOURCES = 0x1,VX_TCP_CODES = 0x2;

    final int port;

    VxRenderer rend;
    VxTCPServer(VxRenderer rend, int port)
    {
        this.port = port;
        this.rend = rend;
    }

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

    // Ensure synchronous access to VxLocalRenderer
    private synchronized void process_codes(lcmvx_render_codes_t lcm_codes)
    {
        // Convert and send off
    }

    private synchronized void process_resources(lcmvx_resource_list_t lcm_resources)
    {

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


                while (true) {

                    int code = ins.readInt();
                    int len = ins.readInt();
                    byte buf[] = new byte[len];
                    ins.read(buf);

                    switch(code) {
                        case VX_TCP_RESOURCES:
                            process_resources(new lcmvx_resource_list_t(new LCMDataInputStream(buf)));
                            break;
                        case VX_TCP_CODES:
                            process_codes(new lcmvx_render_codes_t(new LCMDataInputStream(buf)));
                            break;
                    }
                }

            } catch (EOFException ex) {
                System.out.println("Client disconnected");
            } catch (IOException ex) {
                System.out.println("ex: "+ex);
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

        if (!opts.parse(args) || opts.getBoolean("help") || opts.getExtraArgs().size() > 0) {
            opts.doHelp();
            return;
        }

        // XXX Replace this with a Canvas and give  place to display
        new VxTCPServer(new VxLocalRenderer("java://"),
                        opts.getInt("port"));

    }

}