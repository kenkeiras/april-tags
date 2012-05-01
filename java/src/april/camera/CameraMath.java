package april.camera;

import java.util.*;

import april.jmat.*;

public class CameraMath
{
    /** Triangulation function for points in *rectified image planes*. Radial distortion
      * must be accounted for before calling triangulate()
      **/
    public static double[] triangulate(double[][] Pleft, double[][] Pright,
                                       double[] pxleft, double[] pxright)
    {
        double[] p1t_left = new double[] { Pleft[0][0], Pleft[0][1], Pleft[0][2], Pleft[0][3] };
        double[] p2t_left = new double[] { Pleft[1][0], Pleft[1][1], Pleft[1][2], Pleft[1][3] };
        double[] p3t_left = new double[] { Pleft[2][0], Pleft[2][1], Pleft[2][2], Pleft[2][3] };

        double[] p1t_right = new double[] { Pright[0][0], Pright[0][1], Pright[0][2], Pright[0][3] };
        double[] p2t_right = new double[] { Pright[1][0], Pright[1][1], Pright[1][2], Pright[1][3] };
        double[] p3t_right = new double[] { Pright[2][0], Pright[2][1], Pright[2][2], Pright[2][3] };

        double A[][] = new double[4][];
        A[0] = LinAlg.subtract(LinAlg.scale( p3t_left,  pxleft[0]),  p1t_left);
        A[1] = LinAlg.subtract(LinAlg.scale( p3t_left,  pxleft[1]),  p2t_left);
        A[2] = LinAlg.subtract(LinAlg.scale(p3t_right, pxright[0]), p1t_right);
        A[3] = LinAlg.subtract(LinAlg.scale(p3t_right, pxright[1]), p2t_right);

        Matrix Am = new Matrix(A);
        SingularValueDecomposition SVD = new SingularValueDecomposition(Am);
        Matrix U = SVD.getU();
        Matrix S = SVD.getS();
        Matrix V = SVD.getV();

        double X[] = V.getColumn(3).getDoubles();
        X = LinAlg.scale(X, 1.0/X[3]);

        return LinAlg.select(X, 0, 2);
    }

    public final static double[] pixelTransform(double T[][], double p[])
    {
        if (T.length == 3) {
            if (p.length==2) {
                double r[] = new double[] { T[0][0]*p[0] + T[0][1]*p[1] + T[0][2],
                                            T[1][0]*p[0] + T[1][1]*p[1] + T[1][2],
                                            T[2][0]*p[0] + T[2][1]*p[1] + T[2][2] };

                return new double[] { r[0]/r[2], r[1]/r[2] };
            }
            assert(false);
        }

        assert(false);
        return null;
    }

    public final static double[][] makeCameraMatrix(double K[][], double L2C[][])
    {
        return LinAlg.matrixAB(K, LinAlg.select(L2C, 0, 2, 0, 3));
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Projection methods for 3D points

    // Intrinsics-based projection

    public final static ArrayList<double[]> project(double K[][], double L2C[][], ArrayList<double[]> xyzs)
    {
        ArrayList<double[]> res = new ArrayList<double[]>();
        for (double xyz[] : xyzs)
            res.add(project(K, L2C, xyz));
        return res;
    }

    public final static double[] project(double K[][], double L2C[][], double xyz[])
    {
        double xyz_camera[] = xyz;
        if (L2C != null)
            xyz_camera = LinAlg.transform(L2C, xyz);

        double xy_rn[] = new double[] { xyz_camera[0] / xyz_camera[2] ,
                                        xyz_camera[1] / xyz_camera[2] };
        return pixelTransform(K, xy_rn);
    }

    // View-based projection

    public final static ArrayList<double[]> project(View view, double L2C[][], ArrayList<double[]> xyzs)
    {
        ArrayList<double[]> res = new ArrayList<double[]>();
        for (double xyz[] : xyzs)
            res.add(project(view, L2C, xyz));
        return res;
    }

    public final static double[] project(View view, double L2C[][], double xyz[])
    {
        double xyz_camera[] = xyz;
        if (L2C != null)
            xyz_camera = LinAlg.transform(L2C, xyz);

        double xy_rn[] = new double[] { xyz_camera[0] / xyz_camera[2] ,
                                        xyz_camera[1] / xyz_camera[2] };
        return view.normToPixels(xy_rn);
    }
}
