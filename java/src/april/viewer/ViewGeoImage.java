package april.viewer;

import lcm.lcm.*;
import april.lcmtypes.*;
import april.util.*;
import april.config.*;
import april.vis.*;
import april.jmat.*;
import april.jmat.geom.*;

import java.io.*;
import java.awt.*;
import java.util.*;

public class ViewGeoImage implements ViewObject, LCMSubscriber
{
    Viewer viewer;
    String name;
    Config config;
    LCM         lcm = LCM.getSingleton();
    GeoImage  geoImage;
    VzImage image;
    String channel;

    public ViewGeoImage(Viewer viewer, String name, Config config)
    {
        this.viewer = viewer;
        this.name = name;
        this.config = config;
        this.channel = config.getString("channel", "GPS_TIEPOINT");

        try {
            geoImage = new GeoImage(config.requireString("file"), null);
            image = new VzImage(geoImage.getImage());
            image.modulateColor = new Color(255, 255, 255, config.getInt("alpha", 120));
        } catch (Exception ex) {
            System.out.println("ex: "+ex);
        }

        lcm.subscribe(channel, this);

        update();
    }

    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try {
            messageReceivedEx(channel, ins);
        } catch (IOException ex) {
            System.out.println("Exception: " + ex);
        }
    }

    void update()
    {
        VisWorld.Buffer vb = viewer.getVisWorld().getBuffer("GeoImage: "+channel);

        vb.addBack(new VisChain(geoImage.getMatrix(), image));
        vb.swap();
    }

    void messageReceivedEx(String channel, LCMDataInputStream ins) throws IOException
    {
        gps_tiepoint_t tiepoint = new gps_tiepoint_t(ins);

        GPSLinearization gpslin = new GPSLinearization(tiepoint.lle,
                                                       new double[] { tiepoint.xyzt[0],
                                                                      tiepoint.xyzt[1],
                                                                      tiepoint.xyzt[3] - Math.PI/2 });
        geoImage.setGPSLinearization(gpslin);

        update();
    }
}
