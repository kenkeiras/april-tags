package april.camera.cal;

import april.jmat.*;

public class CameraMath
{
    /** Triangulation function for points in *rectified image planes*. Radial distortion
      * must be accounted for before calling triangulate()
      **/
    public static double[] triangulate(double[][] _Pleft, double[][] _Pright,
                                       double[] pxleft, double[] pxright)
    {
        Matrix Pleft = new Matrix(_Pleft);
        Matrix Pright = new Matrix(_Pright);

        double[] p1t_left = Pleft.getRow(0).getDoubles();
        double[] p2t_left = Pleft.getRow(1).getDoubles();
        double[] p3t_left = Pleft.getRow(2).getDoubles();

        double[] p1t_right = Pright.getRow(0).getDoubles();
        double[] p2t_right = Pright.getRow(1).getDoubles();
        double[] p3t_right = Pright.getRow(2).getDoubles();

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
}
