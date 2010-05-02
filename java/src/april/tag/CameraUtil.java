package april.tag;

import april.jmat.*;

public class CameraUtil
{
    /** Given a 3x3 homography matrix and the focal lengths of the
     * camera, compute the pose of the tag. The focal lengths should
     * be given in pixels. For example, if the camera's focal length
     * is twice the width of the sensor, and the sensor is 600 pixels
     * across, the focal length in pixels is 2*600.
     *
     * Theory: The homography matrix is the product of the camera
     * projection matrix and the tag's pose matrix (the matrix that
     * projects points from the tag's local coordinate system to the
     * camera's coordinate frame).
     *
     * [ h00 h01 h02 h03] = [ 1/fx 0     0 0 ] [ R00 R01 R02 TX ]
     * [ h10 h11 h12 h13] = [ 0    1/fy  0 0 ] [ R10 R11 R12 TY ]
     * [ h20 h21 h22 h23] = [ 0    0     1 0 ] [ R20 R21 R22 TZ ]
     *                                         [ 0   0   0   1  ]
     *
     * When observing a tag, the points we project in world space all
     * have z=0, so we can form a 3x3 matrix by eliminating the 3rd
     * column of the pose matrix.
     *
     * [ h00 h01 h02 ] = [ 1/fx 0     0 0 ] [ R00 R01 TX ]
     * [ h10 h11 h12 ] = [ 0    1/fy  0 0 ] [ R10 R11 TY ]
     * [ h20 h21 h22 ] = [ 0    0     1 0 ] [ R20 R21 TZ ]
     *                                      [ 0   0   1  ]
     *
     * (note that these h's are different from the ones above.)
     *
     * We can multiply the right-hand side to yield a set of equations
     * relating the values of h to the values of the pose matrix.
     *
     * There are two wrinkles. The first is that the homography matrix
     * is known only up to scale. We recover the unknown scale by
     * constraining the magnitude of the first two columns of the pose
     * matrix to be 1. We use the geometric average scale. The sign of
     * the scale factor is recovered by constraining the observed tag
     * to be in front of the camera. Once scaled, we recover the first
     * two columns of the rotation matrix. The third column is the
     * cross product of these.
     *
     * The second wrinkle is that the computed rotation matrix might
     * not be exactly orthogonal, so we perform a polar decomposition
     * to find a good pure rotation approximation.
     **/
    public static double[][] homographyToPose(double fx, double fy, double h[][])
    {
        // The math below is concerned in the length (in pixels) of
        // the camera's focal length PER normalized camera unit. Since
        // the camera observes from ([-1, 1]), the camera's focal
        // length is TWO normalized camera units. Thus, the focal
        // length passed in (which represents the entire width of the
        // sensor) is too large by a factor of two.
        fx *= 0.5;
        fy *= 0.5;

        // flip the homography along the Y axis to align the
        // conventional image coordinate system (y=0 at the top) with
        // the conventional camera coordinate system (y=0 at the
        // bottom).
        double F[][] = LinAlg.identity(3);
        F[1][1] = -1;
        F[2][2] = -1;

        h = LinAlg.matrixAB(F, h);

        double M[][] = new double[4][4];

        // y coordinate values are inverted to account for the
        // fact that we detect targets in *pixel* coordinates, in
        // which y is at the bottom.
        M[0][0] =  h[0][0] / fx;
        M[0][1] =  h[0][1] / fx;
        M[0][3] =  h[0][2] / fx;
        M[1][0] =  h[1][0] / fy;
        M[1][1] =  h[1][1] / fy;
        M[1][3] =  h[1][2] / fy;
        M[2][0] =  h[2][0];
        M[2][1] =  h[2][1];
        M[2][3] =  h[2][2];

        double scale0 = Math.sqrt(sq(M[0][0]) + sq(M[1][0]) + sq(M[2][0]));
        double scale1 = Math.sqrt(sq(M[0][1]) + sq(M[1][1]) + sq(M[2][1]));
        double scale = Math.sqrt(scale0*scale1);

        M = LinAlg.scale(M, 1.0/scale);
        M[3][3] = 1;

        // recover sign of scale factor by noting that observations must occur in front of the camera.
        if (M[2][3] > 0)
            M = LinAlg.scale(M, -1);

        // recover third rotation vector by crossproduct of the other two rotation vectors.
        double a[] = new double[] { M[0][0], M[1][0], M[2][0] };
        double b[] = new double[] { M[0][1], M[1][1], M[2][1] };
        double ab[] = LinAlg.crossProduct(a,b);

        M[0][2] = ab[0];
        M[1][2] = ab[1];
        M[2][2] = ab[2];

        // pull out just the rotation component so we can normalize it.
        double R[][] = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                R[i][j] = M[i][j];

        SingularValueDecomposition svd = new SingularValueDecomposition(new Matrix(R));
        // polar decomposition, R = (UV')(VSV')
        Matrix MR = svd.getU().times(svd.getV().transpose());
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                M[i][j] = MR.get(i,j);

        return M;
    }

    static final double sq(double v)
    {
        return v*v;
    }
}