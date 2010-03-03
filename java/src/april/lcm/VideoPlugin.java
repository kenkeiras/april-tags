package april.lcm;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.geom.*;
import java.io.*;
import javax.swing.*;
import javax.imageio.*;

import lcm.lcm.*;
import lcm.spy.*;

import april.lcmtypes.*;
import april.util.*;

/** A plugin for viewing video_t data **/
public class VideoPlugin implements SpyPlugin
{
    public boolean canHandle(long fingerprint)
    {
        return fingerprint == image_t.LCM_FINGERPRINT;
    }

    class MyAction extends AbstractAction
    {
        ChannelData cd;
        JDesktopPane jdp;

        public MyAction(JDesktopPane jdp, ChannelData cd)
        {
            super("Video Viewer");
            this.jdp = jdp;
            this.cd = cd;
        }

        public void actionPerformed(ActionEvent e)
        {
            Viewer v = new Viewer(cd);
            jdp.add(v);
            v.toFront();
        }
    }

    public Action getAction(JDesktopPane jdp, ChannelData cd)
    {
        return new MyAction(jdp, cd);
    }

    class Viewer extends JInternalFrame implements LCMSubscriber
    {
        ChannelData cd;
        JImage ji;

        public Viewer(ChannelData cd)
        {
            super("Video: "+cd.name, true, true);
            this.cd = cd;

            setLayout(new BorderLayout());
            ji = new JImage(null, true);
            add(ji, BorderLayout.CENTER);
            setSize(400,300);
            setVisible(true);

            LCM.getSingleton().subscribe(cd.name, this);
        }

        BufferedImage handleRAW(image_t v)
        {
            BufferedImage bi = new BufferedImage(v.width, v.height, BufferedImage.TYPE_INT_RGB);

            for (int y = 0; y < v.height; y++) {
                for (int x = 0; x < v.width; x++) {
                    bi.setRGB(x, y, grayToRGB(v.image[x+y*v.stride]));
                }
            }

            return bi;
        }

        BufferedImage handleRGB(image_t v)
        {
            BufferedImage bi = new BufferedImage(v.width, v.height, BufferedImage.TYPE_INT_RGB);

            for (int y = 0; y < v.height; y++) {
                for (int x = 0; x < v.width; x++) {
                    int index = 3*x+y*v.stride;
                    byte r = v.image[index +0];
                    byte g = v.image[index +1];
                    byte b = v.image[index +2];


                    int r_int = r & 0xff;
                    int g_int = g & 0xff;
                    int b_int = b & 0xff;

                    Color col = new Color(r_int,g_int,b_int);
                    bi.setRGB(x, y, col.getRGB());
                }
            }

            return bi;
        }


        BufferedImage handleJPEG(image_t v)
        {
            try {
                return ImageIO.read(new ByteArrayInputStream(v.image));
            } catch (IOException ex) {
                return null;
            }
        }

        public void handleImage(image_t v)
        {
            if (v.width==0 || v.height==0)
                return;

            BufferedImage bi = null;

            switch (v.pixelformat)
            {
                case 1196444237:
                    bi = handleJPEG(v);
                    break;

                case 859981650:
                    bi = handleRGB(v);
                    break;

                default:
                    bi = handleRAW(v);
                    break;

            }

            ji.setImage(bi);
        }

        final int grayToRGB(byte v)
        {
            int g = v&0xff;
            return (g<<16)|(g<<8)|g;
        }

        public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
        {
            try {
                image_t v = new image_t(ins);
                handleImage(v);
            } catch (IOException ex) {
                System.out.println("ex: "+ex);
                return;
            }
        }
    }
}
