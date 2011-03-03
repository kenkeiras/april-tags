package april.velodyne;

import java.util.*;
import java.io.*;
import java.net.*;

import april.lcmtypes.*;
import april.jmat.*;
import april.util.*;

import lcm.lcm.*;

public class VelodyneDriver
{
    DatagramSocket sock;
    LCM lcm = LCM.getSingleton();

    public VelodyneDriver() throws IOException
    {
	sock = new DatagramSocket(2368); //, Inet4Address.getByName("255.255.255.255"));
    }

    public void run() throws IOException
    {
	while (true) {
	    byte buf[] = new byte[8192];
	    DatagramPacket p = new DatagramPacket(buf, buf.length);
	    sock.receive(p);

	    velodyne_t v = new velodyne_t();
	    v.utime = TimeUtil.utime();
	    v.datalen = p.getLength();
	    v.data = buf;

	    lcm.publish("VELODYNE", v);
	}
    }

    public static void main(String args[])
    {
	try {
	    VelodyneDriver driver = new VelodyneDriver();
	    driver.run();
	} catch (IOException ex) {
	    System.out.println("ex: "+ex);
	}
	System.exit(1);
    }
}