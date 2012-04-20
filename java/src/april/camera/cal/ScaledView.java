package april.camera.cal;

import java.util.*;

import april.jmat.*;

public class ScaledView implements View
{
    View view;

    double scale;

    double[][] S;
    double[][] Sinv;

    int width;
    int height;

    public ScaledView(double scale, View view)
    {
        this.view = view;
        this.scale = scale;

        double offs = (1.0 - scale) / 2;

        S = new double[][] { { scale,     0, -offs } ,
                             {     0, scale, -offs } ,
                             {     0,     0,     1 } };
        Sinv = LinAlg.inverse(S);

        width  = (int) Math.floor(view.getWidth() * scale);
        height = (int) Math.floor(view.getHeight() * scale);
    }

    public int          getWidth()
    {
        return width;
    }

    public int          getHeight()
    {
        return height;
    }

    public double[][]   copyIntrinsics()
    {
        return LinAlg.matrixAB(S, view.copyIntrinsics());
    }

    public double[] normToPixels(double xy_rn[])
    {
        return CameraMath.pixelTransform(S, view.normToPixels(xy_rn));
    }

    public double[] pixelsToNorm(double xy_p[])
    {
        return view.pixelsToNorm(CameraMath.pixelTransform(Sinv, xy_p));
    }

    public double[] project(double xyz_camera[])
    {
        double xy_rn[] = new double[] { xyz_camera[0] / xyz_camera[2] ,
                                        xyz_camera[1] / xyz_camera[2] };
        return normToPixels(xy_rn);
    }

    public String       getCacheString()
    {
        return String.format("%s %.12f",
                             view.getCacheString(),
                             scale);
    }
}
