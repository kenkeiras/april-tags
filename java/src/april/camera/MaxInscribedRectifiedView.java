package april.camera;

import java.util.*;

import april.jmat.*;

public class MaxInscribedRectifiedView implements SyntheticView
{
    Calibration cal;

    double      Rb, Rr, Rt, Rl;

    double[][]  K;
    double[][]  Kinv;

    int         width;
    int         height;

    public MaxInscribedRectifiedView(Calibration cal)
    {
        this.cal = cal;

        computeMaxInscribedRectifiedRectangle();
        computeOffsetIntrinsics();
    }

    private void computeMaxInscribedRectifiedRectangle()
    {
        int x_dp, y_dp;

        Rb = cal.rectify(new double[] {               0,                 0})[1];
        Rr = cal.rectify(new double[] {cal.getWidth()-1,                 0})[0];
        Rt = cal.rectify(new double[] {cal.getWidth()-1, cal.getHeight()-1})[1];
        Rl = cal.rectify(new double[] {               0, cal.getHeight()-1})[0];

        // TL -> TR
        y_dp = 0;
        for (x_dp = 0; x_dp < cal.getWidth(); x_dp++) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            Rb = Math.max(Rb, xy_rp[1]);
            //System.out.printf("%6.1f %6.1f - %6.1f\n", xy_rp[0], xy_rp[1], Rb);
        }

        // TR -> BR
        x_dp = cal.getWidth()-1;
        for (y_dp = 0; y_dp < cal.getHeight(); y_dp++) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            Rr = Math.min(Rr, xy_rp[0]);
            //System.out.printf("%6.1f %6.1f - %6.1f\n", xy_rp[0], xy_rp[1], Rr);
        }

        // BR -> BL
        y_dp = cal.getHeight()-1;
        for (x_dp = cal.getWidth()-1; x_dp >= 0; x_dp--) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            Rt = Math.min(Rt, xy_rp[1]);
            //System.out.printf("%6.1f %6.1f - %6.1f\n", xy_rp[0], xy_rp[1], Rt);
        }

        // BL -> TL
        x_dp = 0;
        for (y_dp = cal.getHeight()-1; y_dp >= 0; y_dp--) {

            double xy_rp[] = cal.rectify(new double[] { x_dp, y_dp });
            Rl = Math.max(Rl, xy_rp[0]);
            //System.out.printf("%6.1f %6.1f - %6.1f\n", xy_rp[0], xy_rp[1], Rl);
        }

        System.out.printf("Bottom: %5.1f Right: %5.1f Top: %5.1f Left: %5.1f\n", Rb, Rr, Rt, Rl);
    }

    private void computeOffsetIntrinsics()
    {
        // compute offset for K_r by converting the "top" left image
        // coordinate to pixels with the current camera matrix (K_d)
        K = cal.getIntrinsics();

        K[0][2] -= Rl;
        K[1][2] -= Rb;

        Kinv = LinAlg.inverse(K);

        width   = (int) Math.floor(Rr - Rl + 1);
        height  = (int) Math.floor(Rt - Rb + 1);
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

    public double[][] getIntrinsics()
    {
        return LinAlg.copy(K);
    }

    public double[] distort(double xy_rp[])
    {
        double xy_rp_cal[] = new double[] { xy_rp[0] + Rl ,
                                            xy_rp[1] + Rb };
        return cal.distort(xy_rp_cal);
    }

    public double[] rectify(double xy_dp[])
    {
        double xy_rp_cal[] = cal.rectify(xy_dp);

        return new double[] { xy_rp_cal[0] - Rl ,
                              xy_rp_cal[1] - Rb };
    }

    public double[] project(double xyz_camera[])
    {
        double xy_rn[] = new double[] { xyz_camera[0] / xyz_camera[2] ,
                                        xyz_camera[1] / xyz_camera[2] };

        double xy_rp[][] = LinAlg.matrixAB(K,
                                           new double[][] { { xy_rn[0] },
                                                            { xy_rn[1] },
                                                            {        1 } });
        return new double[] { xy_rp[0][0] / xy_rp[2][0] ,
                              xy_rp[1][0] / xy_rp[2][0] };
    }

    public Calibration getCalibration()
    {
        return this.cal;
    }

    public String getCacheString()
    {
        return String.format("%s %.12f %.12f %.12f %.12f %d %d",
                             cal.getCacheString(),
                             Rb, Rr, Rt, Rl,
                             width, height);
    }
}
