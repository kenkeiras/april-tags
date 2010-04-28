package april.tag;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import april.jmat.*;
import april.jmat.geom.*;

import april.vis.*;
import april.jcam.*;

import april.util.*;

import lcm.lcm.*;
import april.lcmtypes.*;

public class TagTransmit implements ParameterListener
{
    JFrame jf;
    VisWorld  vw = new VisWorld();
    VisCanvas vc = new VisCanvas(vw);

    ImageSource is;

    ParameterGUI pg;

    //    TagFamily tagFamily = new Tag16h5();
    TagFamily tagFamily = new Tag36h11();

    LCM lcm = LCM.getSingleton();

    public static void main(String args[])
    {
        try {
            ArrayList<String> urls = ImageSource.getCameraURLs();

            String url = null;
            if (urls.size()==1)
                url = urls.get(0);

            if (args.length > 0)
                url = args[0];

            if (url == null) {
                System.out.printf("Cameras found:\n");
                for (String u : urls)
                    System.out.printf("  %s\n", u);
                System.out.printf("Please specify one on the command line.\n");
                return;
            }

            ImageSource is = ImageSource.make(url);

            new TagTransmit(is);

        } catch (IOException ex) {
            System.out.println("Ex: "+ex);
        }
    }

    public TagTransmit(ImageSource is)
    {
        this.is = is;

        pg = new ParameterGUI();
        pg.addDoubleSlider("segsigma", "smoothing sigma (segmentation)", 0, 2, 0.8);
        pg.addDoubleSlider("sigma", "smoothing sigma (sampling)", 0, 2, 0.0);
        pg.addDoubleSlider("magthresh", "magnitude threshold", 0.0001, 0.01, .005);
        pg.addDoubleSlider("thetathresh", "theta threshold (radians)", 0, 2*Math.PI, Math.toRadians(35));
        pg.addCheckBoxes("debug", "debug", false);

        jf = new JFrame("TagTest");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);

        jf.setSize(800,600);
        jf.setVisible(true);

        vc.getViewManager().viewGoal.fit2D(new double[] {0,0}, new double[] { 752, 480});
        new RunThread().start();

        pg.addListener(this);
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
    }

    class RunThread extends Thread
    {
        public void run()
        {
            is.start();
            ImageSourceFormat fmt = is.getCurrentFormat();

            TagDetector detector = new TagDetector(tagFamily);

            VisWorld.Buffer vbOriginal = vw.getBuffer("unprocessed image");
            VisWorld.Buffer vbSegmentation = vw.getBuffer("segmentation");
            VisWorld.Buffer vbInput = vw.getBuffer("input");
            VisWorld.Buffer vbDetections = vw.getBuffer("detections");

            detector.debugSegments  = vw.getBuffer("segments");
            detector.debugQuads     = vw.getBuffer("quads");
            detector.debugSamples   = vw.getBuffer("samples");
            detector.debugLabels    = vw.getBuffer("labels");

            while (true) {
                byte buf[] = is.getFrame();
                if (buf == null)
                    continue;

                BufferedImage im = ImageConvert.convertToImage(fmt.format, fmt.width, fmt.height, buf);

                detector.debug = pg.gb("debug");
                detector.sigma = pg.gd("sigma");
                detector.segSigma = pg.gd("segsigma");
                detector.magThresh = pg.gd("magthresh");
                detector.thetaThresh = pg.gd("thetathresh");

                Tic tic = new Tic();
                ArrayList<TagDetection> detections = detector.process(im, new double[] {im.getWidth()/2.0, im.getHeight()/2.0});
                double dt = tic.toc();

                if (detector.debugInput!=null)
                    vbInput.addBuffered(new VisDepthTest(false, new VisLighting(false, new VisImage(detector.debugInput))));
                vbInput.switchBuffer();

                if (detector.debugSegmentation!=null)
                    vbSegmentation.addBuffered(new VisLighting(false, new VisImage(detector.debugSegmentation)));
                vbSegmentation.switchBuffer();

                vbOriginal.addBuffered(new VisDepthTest(false, new VisLighting(false, new VisImage(im))));
                vbOriginal.switchBuffer();

                System.out.printf("***************************** %8.2f ms\n", dt*1000);
                for (TagDetection d : detections) {
                    double p0[] = d.interpolate(-1,-1);
                    double p1[] = d.interpolate(1,-1);
                    double p2[] = d.interpolate(1,1);
                    double p3[] = d.interpolate(-1,1);

                    vbDetections.addBuffered(new VisChain(LinAlg.translate(0, im.getHeight(), 0),
                                                          LinAlg.scale(1, -1, 1),
                                                          new VisText(d.cxy, VisText.ANCHOR.CENTER,
                                                                      String.format("<<center,blue>>id %3d\n(err=%d, rot=%d)\n", d.id, d.hammingDistance, d.rotation)),
                                                          new VisData(new VisDataLineStyle(Color.blue, 4), p0, p1, p2, p3, p0),
                                                          new VisData(new VisDataLineStyle(Color.green, 4), p0, p1), // x axis
                                                          new VisData(new VisDataLineStyle(Color.red, 4), p0, p3))); // y axis

                    System.out.printf("id %3d err %3d\n", d.id, d.hammingDistance);
                }
                vbDetections.switchBuffer();

                tag_detection_list_t dlist = new tag_detection_list_t();
                dlist.utime = System.nanoTime()/1000;
                dlist.width = im.getWidth();
                dlist.height = im.getHeight();
                dlist.ndetections = detections.size();
                dlist.detections = new tag_detection_t[dlist.ndetections];
                for (int i = 0; i < detections.size(); i++) {
                    TagDetection d = detections.get(i);
                    tag_detection_t td = new tag_detection_t();
                    dlist.detections[i] = td;

                    td.id = d.id;
                    td.errors = d.hammingDistance;
                    td.homography = d.homography;
                    td.hxy = d.hxy;
                }

                lcm.publish("TAG_DETECTIONS", dlist);
            }
        }
    }
}
