public class AlignPoints
{

    // returns T, and n+1 by n+1 homogeneous transform points of dimension n
    // from list a to list b using method described in
    // "Least Squares Estimation of Transformation Parameters Between Two Point Patterns"
    // by Shinji Umeyana
    //
    // Algorithm overiew:
    //   a. Compute centroids of both lists.
    //   b. compute M[n][n] = \Sum b_i * a_i^t
    //   c. given M = UDV^t via singular value decomposition, compute rotation
    //      via R = USV^t where S = diag(1,1 .. 1, det(U)*det(V));
    //   d. result computed by compounding differences in centroid and rotation matrix

    public static double det33

    public static double[][] align(List<double[]> a, List<double[]> b)
    {

    }

}