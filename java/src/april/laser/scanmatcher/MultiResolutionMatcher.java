package april.laser.scanmatcher;

import java.util.*;

import april.util.*;
import april.jmat.*;
import april.config.*;

import april.vis.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

// 200 155
// 1700 1700
public class MultiResolutionMatcher
{
    int decimate = 2;
    int ndecimate = 8; // 1 + number of decimated levels

    // our grid maps, with 0 = full resolution. 1 = 1/decimate
    // resolution. 2 = 1/decimate^2 resolution
    GridMap gms[];

    ArrayList<Debug> debugs = new ArrayList<Debug>();
    boolean debug = true;

    static class Debug
    {
        Node n;
        GridMap gm;
        ArrayList<Pt> pts;
    }

    public MultiResolutionMatcher()
    {
        gms = new GridMap[ndecimate];
    }

    public void setModel(GridMap gm0)
    {
        gms = new GridMap[ndecimate];

        if (false) {
            int border = 1024;
            GridMap gmc = GridMap.makePixels(gm0.x0 - border*gm0.metersPerPixel,
                                             gm0.y0 - border*gm0.metersPerPixel,
                                             gm0.width + 2*border,
                                             gm0.height + 2*border,
                                             gm0.metersPerPixel,
                                             gm0.defaultFill,
                                             true);

            for (int y = 0; y < gm0.height; y++) {
                for (int x = 0; x < gm0.width; x++) {
                    gmc.data[(y+border)*gmc.width + (x+border)] = gm0.data[y*gm0.width+x];
                }
            }

            gm0 = gmc;
        }

        gms[0] = gm0;

        for (int i = 1; i < ndecimate; i++) {
            gms[i] = gms[i-1].decimateMax(decimate);
        }

        // When we're translating points at high levels of the image
        // pyramid, we're translating them by relatively large
        // amounts. It's possible that the point was on the far right
        // of its cell, such that it would map to the next grid cell
        // over. If this grid cell had a higher value than the current
        // value, we would underestimate the score. Taking the max4()
        // solves this problem.
        //
        // Example (in 1D). Suppose we have a point mapped to index 3
        // at a quarter-resolution level of the pyramid. We want to
        // evaluate translations from 0-3. For translations 1-3, the
        // point will change buckets.
        for (int i = 1; i < ndecimate; i++) {
            gms[i] = gms[i].max4();
        }
    }

    public double[] match(ArrayList<double[]> points, double prior[], double priorinv[][],
                          double xyt0[],
                          double xrange, double yrange, double trange, double tres)
    {
        Search search = new Search(points, prior, priorinv, xyt0, xrange, yrange, trange, tres);
        return search.compute();
    }

    static class Pt
    {
        // coordinates of the point, after rotation, in units of
        // metersPerPixel (of the highest-resolution gridmap) relative
        // to the gridmap's x0,y0
        int ix, iy;
        int cnt;
    }

    static class Node
    {
        double score; // upper bound on a scan matching result of this node's children.

        double match_score;
        double chi2_score;

        int tidx;
        int gmidx; // resolution (0 = full)

        // what is the search range?  tx0, ty0 are translation offsets
        // in units of metersPerPixel (of the finest resolution grid)
        int tx0, ty0;
        int searchwidth, searchheight; // amount to search, in units of metersPerPixel

        void print()
        {
            System.out.printf("score %15f = %15f + %15f\n", score, match_score, chi2_score);
            System.out.printf("  tidx: %-5d   gmidx: %-5d\n", tidx, gmidx);
            System.out.printf("   tx0: %-5d     ty0: %-5d\n", tx0, ty0);
            System.out.printf("    w: %-5d        h: %-5d\n", searchwidth, searchheight);
        }
    }

    static final double reduceMagnitude(double v, double reduction)
    {
        if (v <= 0)
            return Math.min(0, v + reduction);
        return Math.max(0, v - reduction);
    }

    class Search
    {
        ArrayList<double[]> points;
        double prior[];
        double xyt0[]; // center of search (usually == prior)
        double priorinv[][];
        double xrange, yrange;

        double t0;
        int tcnt; // how many t slices?
        double tres;

        MaxHeap<Object> heap = new MaxHeap<Object>();

        ArrayList<Pt> ptsCache[][]; // tidx, gmidx
        Chi2Data chi2data[];

        Search(ArrayList<double[]> points, double prior[], double priorinv[][],
               double xyt0[],
               double xrange, double yrange, double trange, double tres)
        {
            this.points = points;
            this.prior = prior;
            this.xyt0 = xyt0;
            this.priorinv = priorinv;
            this.xrange = xrange;
            this.yrange = yrange;

            this.t0 = xyt0[2] - trange;
            this.tres = tres;
            this.tcnt = (int) LinAlg.clamp(2*trange/tres + 1, 1, 2*Math.PI / tres);

            ptsCache = new ArrayList[tcnt][ndecimate];

            double priorP[][] = LinAlg.inverse(priorinv);

            chi2data = new Chi2Data[tcnt];
            for (int i = 0; i < tcnt; i++)
                chi2data[i] = new Chi2Data(prior, priorP, t0 + i*tres);
        }

        double[] compute()
        {
            // create an initial set of nodes that search over all the
            // thetas at our coarsest level of resolution.
            for (int tidx = 0; tidx < tcnt; tidx++) {
                Node n = new Node();
                n.gmidx = gms.length - 1;   // coarsest

                // round aggressively...
                n.tidx = tidx;
                n.tx0 = (int) ((xyt0[0]-xrange) / gms[0].metersPerPixel - 1);
                n.ty0 = (int) ((xyt0[1]-yrange) / gms[0].metersPerPixel - 1);
                n.searchwidth = (int) (2*xrange / gms[0].metersPerPixel + 2);
                n.searchheight = (int) (2*yrange / gms[0].metersPerPixel + 2);

                n.match_score = points.size()*255;

                double tx = (n.tx0 + n.searchwidth / 2.0)*gms[0].metersPerPixel;
                double ty = (n.ty0 + n.searchheight / 2.0)*gms[0].metersPerPixel;
                double r = gms[0].metersPerPixel*Math.sqrt(n.searchwidth*n.searchwidth/4.0 + n.searchheight*n.searchheight/4.0);

                n.chi2_score = -chi2data[n.tidx].computeChi2(tx, ty, r);
                n.score = n.match_score + n.chi2_score;

                heap.add(n, n.score);
            }

            // now, keep pulling off search nodes, in order of most
            // promising to least promising. When we arrive at a
            // node that is already at the finest resolution (i.e.,
            // has no children), we're done.

            double lastScore = Double.MAX_VALUE;

            for (int iter = 0; true; iter++) {
                Object heapobj = heap.removeMax();

                if (heapobj instanceof ArrayList) {
                    ArrayList<Node> children = (ArrayList<Node>) heapobj;
                    for (Node child : children)
                        heap.add(child, child.score);
                    continue;
                }

                Node n = (Node) heapobj;

/*
  if (n.score > lastScore)
  System.out.printf("heap ordering violated %15f %15f\n", n.score, lastScore);
  lastScore = n.score;
*/

                if (n == null) {
                    System.out.println("NULL heap entry!");
                    continue;
                }

                if (debug) {
                    // create debug records for all but the initial searches
                    if (n.gmidx!=gms.length-1) {
                        Debug dbg = new Debug();
                        dbg.n = n;
                        dbg.gm = gms[n.gmidx+1];
                        dbg.pts = getPoints(n.tidx, n.gmidx+1); //(int) Math.pow(decimate, n.gmidx+1));
                        debugs.add(dbg);
                    }
                }

                if (n.gmidx == -1) {
                    // we're done!
                    GridMap gm = gms[0];

                    double result[] = new double[] { (n.tx0+.5)*gm.metersPerPixel,
                                                     (n.ty0+.5)*gm.metersPerPixel,
                                                     t0 + n.tidx*tres,
                                                     n.score };

                    return result;
                }

                // expand this node
                GridMap gm = gms[n.gmidx];

                // XXX might be better to invert the search order, i.e.,
                // go over pts first. We can then omit the bounds check
                // and get better locality

                // actually inverse resolution: how many pixels in
                // gms[0] does one pixel in gm represent?
                int resolution = (int) Math.pow(decimate, n.gmidx);

                ArrayList<Pt> pts = getPoints(n.tidx, n.gmidx);

//                System.out.printf("%6d : res %d,  score %15f,  heapsz %6d,  ( xyt %5d %5d %5d ) (sz %5d %5d) pts %d\n",
//                                  iter, n.gmidx, n.score, heap.size(), n.tx0, n.ty0, n.tidx, n.searchwidth, n.searchheight, pts.size());

                ArrayList<Node> children = new ArrayList<Node>();
                Node bestNode = null;

                for (int dy = n.ty0; dy < n.ty0+n.searchheight; dy+=resolution) {
                    for (int dx = n.tx0; dx < n.tx0+n.searchwidth; dx+=resolution) {

                        double score = 0;

                        for (Pt pt : pts) {
                            // (mx,my) are pixel coordinates in gms[0]
                            int mx = (pt.ix + dx);
                            int my = (pt.iy + dy);

                            // we now want to compute the largest
                            // score we could possibly obtain for a
                            // pixel starting at (mx, my) subjected to
                            // translations of [0, resolution-1] in both
                            // x and y directions.
                            int v;

                            // if mx or my is closer to zero, then for
                            // some translation within this block,
                            // we'll appear at pixel 0,0.
                            if (mx <= -resolution || my <= -resolution) {
                                v = (gm.defaultFill&0xff);
                            } else {
                                mx /= resolution;
                                my /= resolution;

                                if (mx >= gm.width || my >= gm.height)
                                    v = gm.defaultFill & 0xff;
                                else
                                    v = (gm.data[my*gm.width + mx] & 0xff);
                            }
                            score += v * pt.cnt;
                        }

                        if (score == 0)
                            continue;

                        // create a new search job
                        double tx = (n.tx0 + n.searchwidth / 2.0)*gms[0].metersPerPixel;
                        double ty = (n.ty0 + n.searchheight / 2.0)*gms[0].metersPerPixel;
                        double r = gms[0].metersPerPixel*Math.sqrt(n.searchwidth*n.searchwidth/4.0 + n.searchheight*n.searchheight/4.0);

                        Node child = new Node();
                        child.gmidx = n.gmidx - 1;
                        child.tidx = n.tidx;
                        child.tx0 = dx;
                        child.ty0 = dy;
                        child.searchwidth = Math.min(n.tx0+n.searchwidth - dx, resolution);
                        child.searchheight = Math.min(n.ty0+n.searchheight - dy, resolution);
                        child.match_score = score;
                        child.chi2_score = -chi2data[child.tidx].computeChi2(tx, ty, r);
                        child.score = child.match_score + child.chi2_score;

                        if (false && child.chi2_score > n.chi2_score+.0001) {
                            System.out.printf("\nchi2 %15f %15f\n", child.chi2_score, n.chi2_score);
                            child.print();
                            n.print();
                        }

                        children.add(child);

                        if (bestNode == null || score > bestNode.score) {
                            bestNode = child;
                        }

//                        System.out.printf("  heap add %15f %15f %15f\n", child.match_score, child.chi2_score, child.score);
//                        heap.add(child, child.score);
                    }
                }


                if (n.gmidx == 0) {
                    // leaf node
                    if (bestNode != null)
                        heap.add(bestNode, bestNode.score);
                } else {
                    // not a leaf node
                    if (children.size() > 0)
                        heap.add(children, bestNode.score);
                }

            }
        }

        // Project our points into integer-valued coordinates relative
        // to (0,0) (not x0,y0).
        ArrayList<Pt> getPoints(int tidx, int gmidx)
        {
            ArrayList<Pt> pts = (ArrayList<Pt>) ptsCache[tidx][gmidx];
            if (pts != null)
                return pts;

            double t = t0 + tidx*tres;
            double s = Math.sin(t), c = Math.cos(t);

            HashMap<Integer, Pt> ptMap = new HashMap<Integer, Pt>(points.size());
            pts = new ArrayList<Pt>();

            GridMap gm = gms[0];
            int resolution = (int) Math.pow(decimate, gmidx);

            for (double p[] : points) {
                int ix = (int) ((c*p[0] - s*p[1] - gm.x0) / gm.metersPerPixel);
                int iy = (int) ((s*p[0] + c*p[1] - gm.y0) / gm.metersPerPixel);

                int key = ((ix / resolution)<<16) + (iy / resolution);
                Pt pt = ptMap.get(key);
                if (pt != null) {
                    pt.cnt ++;
                } else {
                    pt = new Pt();
                    pt.ix = ix;
                    pt.iy = iy;
                    pt.cnt = 1;
                    pts.add(pt);
                    ptMap.put(key, pt);
                }
            }

            ptsCache[tidx][gmidx] = pts;

            return pts;
        }
    }

    public static class DebugViewer implements ParameterListener
    {
        JFrame jf;
        VisWorld vw = new VisWorld();
        VisCanvas vc = new VisCanvas(vw);
        ParameterGUI pg = new ParameterGUI();

        MultiResolutionMatcher matcher;

        public DebugViewer()
        {
            pg.addIntSlider("nodeidx", "node index", 0, 0, 0);

            vc.setBackground(Color.black);
            vw.getBuffer("grid").addFront(new VisGrid());

            jf = new JFrame("ScanMatcher Debug");
            jf.setLayout(new BorderLayout());
            jf.add(vc, BorderLayout.CENTER);
            jf.add(pg, BorderLayout.SOUTH);

            jf.setSize(800,600);
            jf.setVisible(true);

            pg.addListener(this);
        }

        public void set(MultiResolutionMatcher matcher)
        {
            this.matcher = matcher;
            pg.setMinMax("nodeidx", 0, matcher.debugs.size()-1);
            parameterChanged(pg, "nodeidx");
        }

        public void parameterChanged(ParameterGUI pg, String name)
        {
            Debug dbg = matcher.debugs.get(pg.gi("nodeidx"));

            if (true) {
                VisWorld.Buffer vb = vw.getBuffer("gridmap");
                vb.addBuffered(new VisImage(new VisTexture(dbg.gm.makeBufferedImage()), dbg.gm.getXY0(), dbg.gm.getXY1()));
                vb.switchBuffer();
            }

            if (true) {
                VisWorld.Buffer vb = vw.getBuffer("points");
                ArrayList<double[]> ps = new ArrayList<double[]>();
                for (Pt pt : dbg.pts) {
                    ps.add(new double[] { matcher.gms[0].x0 + matcher.gms[0].metersPerPixel*(pt.ix + dbg.n.tx0 + .5),
                                          matcher.gms[0].y0 + matcher.gms[0].metersPerPixel*(pt.iy + dbg.n.ty0 + .5) });
                }
                vb.addBuffered(new VisData(ps, new VisDataPointStyle(Color.magenta, 3)));
                vb.switchBuffer();
            }

            if (true) {
                VisWorld.Buffer vb = vw.getBuffer("score");
                vb.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_RIGHT, String.format("<<red>>score %15f (%15f + %15f), npts %d\nswidth=%5d, sheight=%5d",
                                                                                      Math.min(1E10,dbg.n.score), dbg.n.match_score, dbg.n.chi2_score,
                                                                                      dbg.pts.size(), dbg.n.searchwidth, dbg.n.searchheight)));
                vb.switchBuffer();
            }

        }
    }
}
