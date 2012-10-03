package april.camera;

import java.util.*;

import april.jmat.*;

public class MaxGrownInscribedRectifiedView implements View
{
    public static boolean verbose = false;

    double[][]  K;
    double[][]  Kinv;

    double      Rb, Rr, Rt, Rl;

    int         width;
    int         height;

    String      viewCacheString;

    public MaxGrownInscribedRectifiedView(View view)
    {
        computeMaxGrownInscribedRectifiedRectangle(view);
        viewCacheString = view.getCacheString();
    }

    private void computeMaxGrownInscribedRectifiedRectangle(View view)
    {
        int x_dp, y_dp;

        K = view.copyIntrinsics();

        ////////////////////////////////////////////////////////////////////////////////
        // undistort border

        DistortionFunctionVerifier verifier = new DistortionFunctionVerifier(view);

        ArrayList<double[]> border = new ArrayList<double[]>();

        // TL -> TR
        y_dp = 0;
        for (x_dp = 0; x_dp < view.getWidth(); x_dp++)
        {
            double xy_dp[] = new double[] { x_dp, y_dp };
            xy_dp = verifier.clampPixels(xy_dp);
            double xy_rn[] = view.pixelsToNorm(xy_dp);
            double xy_rp[] = CameraMath.pixelTransform(K, xy_rn);
            border.add(xy_rp);
        }

        // TR -> BR
        x_dp = view.getWidth()-1;
        for (y_dp = 0; y_dp < view.getHeight(); y_dp++)
        {
            double xy_dp[] = new double[] { x_dp, y_dp };
            xy_dp = verifier.clampPixels(xy_dp);
            double xy_rn[] = view.pixelsToNorm(xy_dp);
            double xy_rp[] = CameraMath.pixelTransform(K, xy_rn);
            border.add(xy_rp);
        }

        // BR -> BL
        y_dp = view.getHeight()-1;
        for (x_dp = view.getWidth()-1; x_dp >= 0; x_dp--)
        {
            double xy_dp[] = new double[] { x_dp, y_dp };
            xy_dp = verifier.clampPixels(xy_dp);
            double xy_rn[] = view.pixelsToNorm(xy_dp);
            double xy_rp[] = CameraMath.pixelTransform(K, xy_rn);
            border.add(xy_rp);
        }

        // BL -> TL
        x_dp = 0;
        for (y_dp = view.getHeight()-1; y_dp >= 0; y_dp--)
        {
            double xy_dp[] = new double[] { x_dp, y_dp };
            xy_dp = verifier.clampPixels(xy_dp);
            double xy_rn[] = view.pixelsToNorm(xy_dp);
            double xy_rp[] = CameraMath.pixelTransform(K, xy_rn);
            border.add(xy_rp);
        }

        ////////////////////////////////////////////////////////////////////////////////
        // grow inscribed rectangle

        double centroid[] = new double[2];
        for (double xy[] : border) {
            centroid[0] += xy[0]/border.size();
            centroid[1] += xy[1]/border.size();
        }

        // corner case
        int xmin = (int) centroid[0];
        int xmax = (int) centroid[0];
        int ymin = (int) centroid[1];
        int ymax = (int) centroid[1];

        boolean changed = true;
        while (changed)
        {
            changed = false;

            if (acceptMove(border, xmin-1, xmax, ymin, ymax)) {
                changed = true;
                xmin--;
            }

            if (acceptMove(border, xmin, xmax+1, ymin, ymax)) {
                changed = true;
                xmax++;
            }

            if (acceptMove(border, xmin, xmax, ymin-1, ymax)) {
                changed = true;
                ymin--;
            }

            if (acceptMove(border, xmin, xmax, ymin, ymax+1)) {
                changed = true;
                ymax++;
            }

            double area = (xmax - xmin)*(ymax - ymin);
            if (area > 10*view.getWidth()*view.getHeight()) { // XXX
                break;
            }
        }

        Rb = ymin;
        Rt = ymax;
        Rl = xmin;
        Rr = xmax;

        if (verbose) System.out.printf("Bottom: %5.1f Right: %5.1f Top: %5.1f Left: %5.1f\n", Rb, Rr, Rt, Rl);

        ////////////////////////////////////////////////////////////////////////////////
        // transformation matrix
        K[0][2] -= Rl;
        K[1][2] -= Rb;
        Kinv = LinAlg.inverse(K);

        width   = (int) Math.floor(Rr - Rl + 1);
        height  = (int) Math.floor(Rt - Rb + 1);
    }

    private boolean acceptMove(ArrayList<double[]> border, double xmin, double xmax, double ymin, double ymax)
    {
        for (double xy[] : border) {
            if (xy[0] > xmin && xy[0] < xmax && xy[1] > ymin && xy[1] < ymax)
                return false;
        }

        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Calibration interface methods

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    public double[][] copyIntrinsics()
    {
        return LinAlg.copy(K);
    }

    public double[] normToPixels(double xy_rn[])
    {
        return CameraMath.pixelTransform(K, xy_rn);
    }

    public double[] pixelsToNorm(double xy_rp[])
    {
        return CameraMath.pixelTransform(Kinv, xy_rp);
    }

    public String getCacheString()
    {
        return String.format("%s %.12f %.12f %.12f %.12f %d %d",
                             viewCacheString,
                             Rb, Rr, Rt, Rl,
                             width, height);
    }
}

