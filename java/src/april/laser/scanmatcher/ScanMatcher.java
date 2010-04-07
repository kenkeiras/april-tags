package april.laser.scanmatcher;

import april.config.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.graph.*;
import april.util.*;
import april.laser.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;

public class ScanMatcher
{
    Config config;
    Graph  g;
    GridMap gm;

    ContourExtractor contourExtractor;

    double metersPerPixel;
    double thetaResolution;

    ArrayList<Scan> scans = new ArrayList<Scan>();

    // Represents one of our historical scans which we use to build our local model.
    static class Scan
    {
        double xyt[];  // x, y, theta of robot

        // 2D points in global coordinates
        ArrayList<double[]> gpoints;

        // contours, in global coordinate frame.
        ArrayList<ArrayList<double[]>> gcontours;
    }

    public ScanMatcher(Config config)
    {
        this.config = config;

        this.g = new Graph();

        contourExtractor = new ContourExtractor(config);

        this.metersPerPixel = config.getDouble("scanmatcher.meters_per_pixel", 0.025);
        this.thetaResolution = Math.toRadians(config.getDouble("scanmatcher.theta_search_resolution_deg", 20));
    }


    public void processOdometry(double xyt[], double P[][])
    {
    }


    /** Points should be projected into robot's coordinate frame. **/
    public void processScan(ArrayList<double[]> rpoints)
    {
        if (scans.size() == 0) {
            // our first-ever scan.
            double pos[] = new double[3];
            GXYTNode gn = new GXYTNode();
            gn.state = pos;
            gn.init = pos;
            gn.truth = pos;

            gn.setAttribute("points", new ArrayList<double[]>(rpoints), new april.util.PointArrayCoder());
            g.nodes.add(gn);

            Scan scan = new Scan();
            scan.xyt = new double[3];
            scan.gpoints = new ArrayList<double[]>(rpoints);
            scan.gcontours = contourExtractor.getContours(scan.gpoints);
        }
    }

    void updateRaster()
    {
        double minx = Double.MAX_VALUE, maxx = -Double.MAX_VALUE;
        double miny = Double.MAX_VALUE, maxy = -Double.MAX_VALUE;

        // Compute bounds of the scans.
        for (Scan s : scans) {
            for (double p[] : s.gpoints) {
                minx = Math.min(minx, p[0]);
                maxx = Math.max(maxx, p[0]);
                miny = Math.min(miny, p[1]);
                maxy = Math.max(maxy, p[1]);
            }
        }

        // create the LUT, with a bit more space than we currently
        // think we'll need (the thought being that we could add new
        // scans without having to rebuild the whole damn thing
        double margin = Math.max(1, 0.1*Math.max(maxx-minx, maxy-miny));

        gm = new GridMap((minx+maxx)/2, (miny+maxy)/2, (maxx-minx)+2*margin, (maxy-miny)+2*margin, metersPerPixel, 0);
        GridMap.LUT lut = gm.makeExponentialLUT(1.0, 0, 0.1); // XXX

        for (int sidx = 0; sidx < scans.size(); sidx++) {
            Scan s = scans.get(sidx);

            for (ArrayList<double[]> c : s.gcontours) {
                for (int i = 0; i+1 < c.size(); i++) {
                    double p0[] = c.get(i);
                    double p1[] = c.get(i+1);

                    double length = LinAlg.distance(p0, p1);

                    gm.drawRectangle((p0[0]+p1[0])/2, (p0[1]+p1[1])/2, length, 0,
                                     Math.atan2(p1[1]-p0[1], p1[0]-p0[0]),
                                     lut);
                }
            }
        }

    }
}
