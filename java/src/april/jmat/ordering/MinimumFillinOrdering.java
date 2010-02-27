package april.jmat.ordering;

import april.jmat.*;

import java.util.*;

/** Greedy minimum fill in ordering. **/
public class MinimumFillinOrdering implements Ordering
{
    IntHashSet nodeSet = new IntHashSet();

    Node nodes[];
    int perm[];   // the permutation we're building

    IntHashSet tmpset = new IntHashSet();

    public MinimumFillinOrdering()
    {
    }

    public int[] getPermutation(Matrix A)
    {
        int m = A.getRowDimension();
        int n = A.getColumnDimension();

        tmpset.ensureCapacity(n);

        nodes = new Node[n];
        perm = new int[n];

        // create graph data structure
        for (int i = 0; i < n; i++) {
            nodes[i] = new Node(i);

            Vec row = A.getRow(i);

            if (row instanceof CSRVec) {
                CSRVec crow = (CSRVec) row;
                for (int j = 0; j < crow.nz; j++)
                    if (crow.indices[j]!=i)
                        nodes[i].addNeighbor(crow.indices[j]);

            } else {
                for (int j = 0; j < m; j++) {
                    if (i!=j && A.get(i,j)!=0)
                        nodes[i].addNeighbor(j);
                }
            }
        }

        // compute initial costs
        for (int i = 0; i < n; i++)
            nodes[i].cost = Integer.MIN_VALUE;

        //////////////
        // Repeatedly pick the best node
        for (int i = 0; i < n; i++) {

            // find the best node.
            Node bestNode = null;
            int  bestCost = Integer.MAX_VALUE;

            for (int j = 0; j < n; j++) {
                Node node = nodes[j];
                if (node.used)
                    continue;

                if (node.cost == Integer.MIN_VALUE)
                    updateCost(j);

                if (node.cost < bestCost) {
                    bestNode = node;
                    bestCost = node.cost;
                }
            }

            perm[i] = bestNode.sidx;
            bestNode.used = true;

            // marginalize out this node, connecting all of its
            // neighbors.
            for (int k = 0; k < bestNode.nneighbors; k++)
                nodes[bestNode.neighbors[k]].removeNodeAndAddNeighbors(bestNode, nodes.length, tmpset);

            // mark costs of neighbors (and /their/ neighbors) as
            // needing to be recomputed.
            for (int j = 0; j < bestNode.nneighbors; j++) {
                Node node = nodes[bestNode.neighbors[j]];
                node.cost = Integer.MIN_VALUE;
                for (int k = 0; k < node.nneighbors; k++) {
                    nodes[node.neighbors[k]].cost = Integer.MIN_VALUE;
                }
            }
        }

        return perm;
    }

    public void updateCost(int idx)
    {
        Node node = nodes[idx];

        // how many edges would we create if we added this?
        int fillin = 0;
        for (int k = 0; k < node.nneighbors; k++)
            fillin+=nodes[node.neighbors[k]].removeNodeAndAddNeighborsDryRun(node, nodes.length, tmpset);

        node.cost = fillin;
    }
}
