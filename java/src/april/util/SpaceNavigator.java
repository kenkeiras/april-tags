package april.util;

import java.io.*;
import java.util.*;

import april.jmat.*;
import april.util.*;

public class SpaceNavigator
{
    boolean hexdump;

    String path = "/dev/spacenav";

    int values[] = new int[8];
    boolean button_event = false;

    ArrayList<Listener> listeners = new ArrayList<Listener>();

    public SpaceNavigator(boolean hexdump)
    {
        this.hexdump = hexdump;

        // initialize stream
        FileInputStream f = null;
        try {
            f = new FileInputStream(path);
        } catch (IOException e) {
            System.err.printf("Error: no device at path '%s'\n",
                               path);
            System.out.println("Exception: e");
            e.printStackTrace();
        }

        if (f == null) {
            System.err.println("Exiting.");
            System.exit(-1);
        }

        // start parsing thread
        RunThread t = new RunThread(f);
        t.start();
    }

    public class MotionEvent
    {
        public int x;
        public int y;
        public int z;

        public int roll;
        public int pitch;
        public int yaw;

        public boolean left;
        public boolean right;

        public double[][] getMatrix()
        {
            return LinAlg.xyzrpyToMatrix(new double[] {x, y, z, roll, pitch, yaw});
        }

        public MotionEvent(int values[])
        {
            x = values[0];
            y = values[1];
            z = values[2];
            roll = values[3];
            pitch = values[4];
            yaw = values[5];

            left  = (values[6] == 1);
            right = (values[7] == 1);
        }
    }

    public interface Listener
    {
        public void handleUpdate(MotionEvent me);
    }

    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    private class RunThread extends Thread
    {
        FileInputStream is;

        public RunThread(FileInputStream is)
        {
            this.is = is;
        }

        public void run()
        {
            while (true) {
                byte buf[] = new byte[24];

                try {
                    int r = is.read(buf);

                    short type  = (short) (((buf[17] & 0xFF) << 8) | ((buf[16] & 0xFF)));
                    short index = (short) (((buf[19] & 0xFF) << 8) | ((buf[18] & 0xFF)));
                    int value   = ((buf[23] & 0xFF) << 24) | ((buf[22] & 0xFF) << 16) | ((buf[21] & 0xFF) << 8) | ((buf[20] & 0xFF));

                    switch (type) {
                        // motion event
                        case 2:
                            values[index] = value;
                            button_event = false;
                            break;

                        // key press
                        case 4:
                            button_event = true;
                            break;

                        // key release
                        case 1:
                            int button_id = index & 0xFF;
                            int depressed = value;

                            values[button_id + 6] = depressed;

                            // clear the button event
                            button_event = false;
                            break;

                        // flush
                        case 0:
                            // notify listeners
                            for (Listener listener : listeners)
                                listener.handleUpdate(new MotionEvent(values));

                            break;

                        // other
                        default:
                            System.out.printf("Unrecognized type: %3d index: %3d value: %5d\n",
                                              type, index, value);
                            break;
                    }

                    if (hexdump) {
                        for (int i=0; i < buf.length; i++)
                            System.out.printf("%2X ", buf[i] & 0xFF);
                        System.out.println();
                    }

                } catch (IOException e) {
                    System.err.println("Exception: "+e);
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addBoolean('v',"verbose",false,"Verbose output");
        opts.addBoolean('x',"hexdump",false,"Enable hex dump");

        if (!opts.parse(args)) {
            System.out.println("option error: "+opts.getReason());
	    }

        if (opts.getBoolean("help")) {
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        boolean verbose = opts.getBoolean("verbose");
        boolean hexdump = opts.getBoolean("hexdump");

        if (verbose && hexdump) {
            System.out.println("Warning: hexdump switch disables verbosity");
            verbose = false;
        }

        SpaceNavigator sn = new SpaceNavigator(hexdump);
        if (verbose)
            sn.addListener(new ExampleListener());
    }
}

class ExampleListener implements SpaceNavigator.Listener
{
    public ExampleListener()
    {
    }

    @Override
    public void handleUpdate(SpaceNavigator.MotionEvent me)
    {
        System.out.printf("[MotionEvent] x: %4d y: %4d z: %4d roll: %4d pitch: %4d yaw: %4d left: %d right: %d\n",
                          me.x, me.y, me.z, me.roll, me.pitch, me.yaw, me.left ? 1 : 0, me.right ? 1 : 0);
    }
}
