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

    /** Estimate the camera intrinsics matrix K from a list of vanishing point pairs (two vanishing points per image).
      *
      * @param vanishingPointPairs - A list of vanishing point pairs {{u0, v0}, {u1, v1}, {u2, v2}, ...} specified
      * as two vanishing points per plane in the image (e.g. an AprilTag or tag calibration mosaic). At least 3 planar
      * observations are required to estimate the intrinsics. Null vanishing points are allowed for convenience and
      * will be skipped.
      */
    public final static double[][] estimateIntrinsicsFromVanishingPoints(ArrayList<double[][]> vanishingPointPairs)
    {
        int numVanishingPoints = 0;
        for (double vp[][] : vanishingPointPairs)
            if (vp != null)
                numVanishingPoints++;

        if (numVanishingPoints < 3)
            return null;

        double A[][] = new double[numVanishingPoints+2][];

        // vanishing points
        int i=0;
        for (int n=0; n < vanishingPointPairs.size(); n++) {
            double vp[][] = vanishingPointPairs.get(n);
            if (vp == null)
                continue;

            double u[] = new double[] { vp[0][0], vp[0][1], 1 };
            double v[] = new double[] { vp[1][0], vp[1][1], 1 };

            // normalization is essential for good estimates
            u = LinAlg.normalize(u);
            v = LinAlg.normalize(v);

            double u1 = u[0];
            double u2 = u[1];
            double u3 = u[2];

            double v1 = v[0];
            double v2 = v[1];
            double v3 = v[2];

            double row[] = new double[] { u1*v1,
                                          u1*v2 + u2*v1,
                                          u2*v2,
                                          u1*v3 + u3*v1,
                                          u2*v3 + u3*v2,
                                          u3*v3 };

            A[i++] = row;
        }

        // zero skew
        A[i++] = new double[] { 0, 1, 0, 0, 0, 0 };

        // similar focal lengths
        A[i++] = new double[] { 1, 0,-1, 0, 0, 0 };

        SingularValueDecomposition SVD = new SingularValueDecomposition(new Matrix(A));
        Matrix V = SVD.getV();

        double w[] = V.getColumn(V.getColumnDimension()-1).getDoubles();

        double omega[][] = new double[3][3];
        omega[0][0] = w[0];
        omega[0][1] = w[1];
        omega[0][2] = w[3];
        omega[1][0] = w[1];
        omega[1][1] = w[2];
        omega[1][2] = w[4];
        omega[2][0] = w[3];
        omega[2][1] = w[4];
        omega[2][2] = w[5];

        CholeskyDecomposition cd = new CholeskyDecomposition(new Matrix(omega));
        if (!cd.isSPD())
            cd = new CholeskyDecomposition(new Matrix(LinAlg.scale(omega, -1)));

        double Kinv[][] = cd.getL().transpose().copyArray();
        Kinv = LinAlg.scale(Kinv, 1.0 / Kinv[2][2]);

        double K[][] = LinAlg.inverse(Kinv);

        return K;
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

    ////////////////////////////////////////////////////////////////////////////////

    public final static double[][] makeVisPlottingTransform(View view, double XY0[], double XY1[], boolean flip)
    {
        return makeVisPlottingTransform(view.getWidth(), view.getHeight(), XY0, XY1, flip);
    }

    public final static double[][] makeVisPlottingTransform(int imwidth, int imheight,
                                                            double XY0[], double XY1[], boolean flip)
    {
        double viswidth = XY1[0] - XY0[0];
        double visheight = XY1[1] - XY0[1];

        double T[][] = LinAlg.matrixAB(LinAlg.translate(XY0[0], XY1[1], 0),
                                       LinAlg.scale(viswidth / imwidth,
                                                    visheight / imheight,
                                                    1));

        if (flip)
            return LinAlg.matrixAB(T,
                                   LinAlg.scale(1, -1, 1));
        else
            return T;
    }
}