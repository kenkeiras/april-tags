package april.jmat.ordering;

import april.jmat.*;

/** Generate a permutation that attempts to reduce the amount of fillin (experimental). **/
public class ApproximateFillinOrdering
{
    int perm[];

    public ApproximateFillinOrdering(Matrix A)
    {
        int m = A.getRowDimension(), n = A.getColumnDimension();

        perm = new int[m];

        // count the number of ZERO entries in each column.
        double colzs[] = new double[A.getColumnDimension()];
        for (int i = 0; i < A.getRowDimension(); i++) {
            for (int j = 0; j < A.getColumnDimension(); j++) {

                if (A.get(i,j)==0)
                    colzs[j]++;
            }
        }

        assert(m==n); // untested otherwise.

        boolean used[] = new boolean[m];

        for (int cidx = 0; cidx < n; cidx++) {

            double bestfillin = Double.MAX_VALUE;
            int    bestj = -1;

            for (int j = 0; j < n; j++) {
                if (used[j])
                    continue;

                // we'll create fill-in where row_j != 0 and any
                // other row 'other' needs col j eliminated (other_j !=0)
                // and (row_j != 0 && other_i == 0)

                // first, how much fillin would there be if *every*
                // row had other_j != 0? We'd squash all the zeros in
                // the columns in which row_j !=0

                double fillin = 0;

                Vec Arowj = A.getRow(j);

                if (Arowj instanceof CSRVec) {
                    CSRVec cv = (CSRVec) Arowj;

                    for (int i = 0; i < cv.nz; i++)
                        fillin += colzs[cv.indices[i]];

                } else {
                    for (int i = 0; i < m; i++)
                        if (A.get(j,i)!=0)
                            fillin += colzs[i];
                }

                // now, adjust that amount by scaling it by the number of rows
                // that actually have other_j != 0
                double rowsleft = (n - cidx);
                double frac = (rowsleft - colzs[j])/rowsleft;
                if (frac<0)
                    frac =0;
                if (frac>1)
                    frac = 1;

                frac = 1;
                fillin *= frac;

                if (fillin < bestfillin) {
                    bestfillin = fillin;
                    bestj = j;
                }
            }

            // okay, we'll pick bestj.
            perm[cidx] = bestj;
            used[bestj] = true;

            System.out.printf("%5d %5d\n", cidx, bestj);
            // we need to update the number of zero entries based on
            // our predicted fill-in.

            double rowsleft = (n - cidx);
            double frac = (rowsleft - colzs[bestj])/rowsleft;
            if (frac<0)
                frac =0;
            if (frac>1)
                frac = 1;

            frac = 1;

            for (int i = 0; i < m; i++)
                if (A.get(bestj,i)!=0)
                    colzs[i] *= (1.0 - frac);
        }
    }

    public int[] getPermutation()
    {
        return perm;
    }
}
