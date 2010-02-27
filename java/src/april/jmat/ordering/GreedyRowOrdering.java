package april.jmat.ordering;

import april.jmat.*;

/** Finds a row ordering by greedily selecting the next best variable
 * (work in progress).
 **/
public class GreedyRowOrdering
{
    Matrix M;
    int perm[];

    public GreedyRowOrdering(Matrix M)
    {
        perm = new int[M.getRowDimension()];

        boolean rowUsed[] = new boolean[M.getRowDimension()];

        // count the number of non-zero entries in each column,
        // counting only the rows that have not yet been used.
        int colzs[] = new int[M.getColumnDimension()];

        for (int i = 0; i < M.getRowDimension(); i++) {
            for (int j = 0; j < M.getColumnDimension(); j++) {

                if (M.get(i,j)==0)
                    colzs[j]++;
            }
        }

        ///////////////////////////////////////////////////
        // Begin searching for permutation

        int totalfillin = 0;

        // find a good row for eliminating column cidx
        for (int cidx = 0; cidx < M.getColumnDimension(); cidx++) {

            int bestfillin = Integer.MAX_VALUE;
            int bestrow = 0;

            for (int ridx = 0; ridx < M.getRowDimension(); ridx++) {

                if (rowUsed[ridx])
                    continue;

                int fillin = 0;
                for (int i = 0; i < M.getColumnDimension(); i++) {
                    if (M.get(ridx, i)!=0)
                        fillin += colzs[i];
                }

                if (fillin < bestfillin) {
                    bestfillin = fillin;
                    bestrow = ridx;
                }
            }

            // we use bestrow!
            perm[cidx] = bestrow;
            rowUsed[bestrow] = true;
            for (int i = 0; i < M.getColumnDimension(); i++) {
                if (M.get(bestrow,i)!=0)
                    colzs[i]=0;
            }
            totalfillin += bestfillin;

            //	    System.out.printf("%4d %4d (%10d)\n", cidx, bestrow, bestfillin);
        }

        System.out.println("total fillin estimate: "+totalfillin);
    }

    public int[] getPermutation()
    {
        return perm;
    }
}
