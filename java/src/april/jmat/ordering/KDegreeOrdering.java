package april.jmat.ordering;

import april.jmat.*;

import java.util.*;

/** Totally experimental: doesn't work well.
 **/
public class KDegreeOrdering implements Ordering
{
    public double k = 0.5;

    static class Node implements Comparable<Node>
    {
        int index;
        ArrayList<Integer> kneighbors;

        double kdegree;

        public int compareTo(Node n)
        {
            return Double.compare(kdegree, n.kdegree);
        }
    }

    public KDegreeOrdering(double k)
    {
        this.k = k;
    }

    public int[] getPermutation(Matrix A)
    {
        ArrayList<Node> nodes = new ArrayList<Node>();

        int nrows = A.getRowDimension();
        int ncols = A.getColumnDimension();

        // compute the neighbors for each node into a list.
        ArrayList<ArrayList<Integer>> neighbors = new ArrayList<ArrayList<Integer>>();
        for (int row = 0; row < nrows; row++) {

            ArrayList<Integer> theseneighbors = new ArrayList<Integer>();
            neighbors.add(theseneighbors);

            for (int col = 0; col < ncols; col++) {
                if (A.get(row, col) != 0) {
                    theseneighbors.add(col);
                }
            }
        }

        // find the size of the k-neighbor hood for node gnidx
        for (int idx = 0; idx < ncols; idx++) {
            Node node = new Node();
            nodes.add(node);
            node.index = idx;
            node.kneighbors = new ArrayList<Integer>(nodes.size());

            // do a breadth-first search with loop detection.
            HashSet<Integer> visited = new HashSet<Integer>(nodes.size());
            ArrayList<Integer> fringe = new ArrayList<Integer>(nodes.size());

            fringe.add(idx);
            visited.add(idx);

            // how many neighbors at depth k?
            for (int k = 0; fringe.size() > 0; k++) {

                int nodesadded = 0;

                ArrayList<Integer> newfringe = new ArrayList<Integer>();

                for (int n : fringe) {
                    for (int neighbor : neighbors.get(n)) {

                        if (!visited.contains(neighbor)) {
                            newfringe.add(neighbor);
                            visited.add(neighbor);
                            nodesadded++;
                        }
                    }
                }

                fringe = newfringe;

                node.kneighbors.add(nodesadded);
            }
        }

        for (Node n : nodes) {

            double acc = 0;
            double weight = 1.0;

            int totalnodes = 0;

            double lever = 0;

            for (int i = 0; i < n.kneighbors.size(); i++) {
                acc += weight*n.kneighbors.get(i);
                weight *= k;

                lever += i * n.kneighbors.get(i);
                totalnodes += n.kneighbors.get(i);
                //		System.out.printf("%4d ", n.kneighbors.get(i));
            }
            //	    System.out.printf("\n");

            //	    n.kdegree = acc;
            n.kdegree = 1.0/lever;

            //	    System.out.printf("%f\n", lever / (nodes.size()-1));
            assert(totalnodes == nodes.size()-1);
        }

        Collections.sort(nodes);
        int perm[] = new int[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            perm[i] = nodes.get(i).index;
        }

        return perm;
    }
}
