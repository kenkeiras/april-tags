package april.camera.cal;

import java.util.*;

import april.jmat.*;

public class MaxInscribedRectifiedView implements View
{
    double[][]  K;
    double[][]  Kinv;

    double      Rb, Rr, Rt, Rl;

    int         width;
    int         height;

    String      viewCacheString;

    public MaxInscribedRectifiedView(View view)
    {
        computeMaxInscribedRectifiedRectangle(view);
        viewCacheString = view.getCacheString();
    }

    private void computeMaxInscribedRectifiedRectangle(View view)
    {
        int x_dp, y_dp;

        K = view.copyIntrinsics();

        Rb = CameraMath.pixelTransform(K, view.pixelsToNorm(new double[] {                0,                  0}))[1];
        Rr = CameraMath.pixelTransform(K, view.pixelsToNorm(new double[] {view.getWidth()-1,                  0}))[0];
        Rt = CameraMath.pixelTransform(K, view.pixelsToNorm(new double[] {view.getWidth()-1, view.getHeight()-1}))[1];
        Rl = CameraMath.pixelTransform(K, view.pixelsToNorm(new double[] {                0, view.getHeight()-1}))[0];

        // TL -> TR
        y_dp = 0;
        for (x_dp = 0; x_dp < view.getWidth(); x_dp++) {

            double xy_rp[] = CameraMath.pixelTransform(K, view.pixelsToNorm(new double[] { x_dp, y_dp }));
            Rb = Math.max(Rb, xy_rp[1]);
            //System.out.printf("%6.1f %6.1f - %6.1f\n", xy_rp[0], xy_rp[1], Rb);
        }

        // TR -> BR
        x_dp = view.getWidth()-1;
        for (y_dp = 0; y_dp < view.getHeight(); y_dp++) {

            double xy_rp[] = CameraMath.pixelTransform(K, view.pixelsToNorm(new double[] { x_dp, y_dp }));
            Rr = Math.min(Rr, xy_rp[0]);
            //System.out.printf("%6.1f %6.1f - %6.1f\n", xy_rp[0], xy_rp[1], Rr);
        }

        // BR -> BL
        y_dp = view.getHeight()-1;
        for (x_dp = view.getWidth()-1; x_dp >= 0; x_dp--) {

            double xy_rp[] = CameraMath.pixelTransform(K, view.pixelsToNorm(new double[] { x_dp, y_dp }));
            Rt = Math.min(Rt, xy_rp[1]);
            //System.out.printf("%6.1f %6.1f - %6.1f\n", xy_rp[0], xy_rp[1], Rt);
        }

        // BL -> TL
        x_dp = 0;
        for (y_dp = view.getHeight()-1; y_dp >= 0; y_dp--) {

            double xy_rp[] = CameraMath.pixelTransform(K, view.pixelsToNorm(new double[] { x_dp, y_dp }));
            Rl = Math.max(Rl, xy_rp[0]);
            //System.out.printf("%6.1f %6.1f - %6.1f\n", xy_rp[0], xy_rp[1], Rl);
        }

        System.out.printf("Bottom: %5.1f Right: %5.1f Top: %5.1f Left: %5.1f\n", Rb, Rr, Rt, Rl);

        ////////////////////////////////////////////////////////////////////////////////
        // transformation matrix
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

    public double[] project(double xyz_camera[])
    {
        double xy_rn[] = new double[] { xyz_camera[0] / xyz_camera[2] ,
                                        xyz_camera[1] / xyz_camera[2] };
        return normToPixels(xy_rn);
    }

    public String getCacheString()
    {
        return String.format("%s %.12f %.12f %.12f %.12f %d %d",
                             viewCacheString,
                             Rb, Rr, Rt, Rl,
                             width, height);
    }
}
