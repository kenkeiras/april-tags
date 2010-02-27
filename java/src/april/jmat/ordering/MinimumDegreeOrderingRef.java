package april.jmat.ordering;

import april.jmat.*;

import java.util.*;

/** This is an exact minimum-degree ordering method to allow
 * comparisons to approximations. Despite being exact, it's actually
 * pretty fast.
 *
 * The neighbors of each variable are maintained in an unordered list.
 * When marginalizing out a node, all its neighbors become fully
 * connected (but lose their reference to the marginalized-out node).
 * Duplicate items are detected (and thus removed) using a hash set.
 *
 * We then repeatedly pick the variable with the lowest degree.
 **/
public class MinimumDegreeOrderingRef implements Ordering
{
    // part of our hashset union logic, described below.  we use the
    // flags array to detect duplicate node ids when merging two sets
    // of nodes.
    int flags[];
    int nextFlag = 1;

    Node nodes[];
    int perm[];   // the permutation we're building

    class Node
    {
        int sidx; // self index. nodes[sidx] == this.

        boolean used = false;

        // an un-ordered list of neighbors. 16 is an initial capacity
        int neighbors[] = new int[16];

        // how many neighbors are valid?
        int nneighbors;

        public Node(int sidx)
        {
            this.sidx = sidx;
        }

        // increase the internal capacity of the neighbors list
        final void grow()
        {
            int newsz = Math.min(neighbors.length*2, nodes.length);
            int newneighbors[] = new int[newsz];
            System.arraycopy(neighbors, 0, newneighbors, 0, nneighbors);
            neighbors = newneighbors;
        }

        // n must not already be a neighbor.
        public final void addNeighbor(int n)
        {
            if (nneighbors == neighbors.length)
                grow();

            neighbors[nneighbors++] = n;
        }

        // how many neighbors?
        public final int size()
        {
            return nneighbors;
        }

        /** Add all of the neighbors of 'node' to our set of neighbors,
            and remove node. This is used during marginalization.
        **/
        final void removeNodeAndAddNeighbors(Node node)
        {
            // allocate enough room for the worst case.
            int maxsize = Math.min(node.nneighbors + nneighbors, nodes.length);
            while (neighbors.length < maxsize)
                grow();

            // We use the "flags" array as a fast-and-dirty hashset
            // for node indices.  Rather than clearing it at each
            // turn, we increment a value that corresponds to
            // "set". All other values are "not set".
            int flag = nextFlag;
            nextFlag++;

            // handle wrap around (once in 2^32 operations!) by resetting.
            if (nextFlag==0) {
                // must reset every entry to avoid accidental collision
                for (int i = 0; i < nodes.length; i++)
                    flags[i] = 0;
                nextFlag = 1;
            }

            if (flags == null)
                flags = new int[nodes.length];

            int outidx = 0;

            // disallow any neighbor nodes corresponding to either
            // our own sidx or node.sidx.
            flags[sidx] = flag;
            flags[node.sidx] = flag;

            //////////////////////////////////
            // populate the hash set with our already-known neighbors.
            // We will come across node.sidx exactly once, which we will
            // shuffle-remove.
            int pos = 0; // remember position of node.sidx.

            for (int i = 0; i < nneighbors; i++) {
                int n = neighbors[i];
                if (n==node.sidx)
                    pos = i;

                flags[n] = flag;
            }

            // shuffle remove node.sidx at position 'pos'.
            neighbors[pos] = neighbors[nneighbors-1];
            nneighbors--;
            outidx = nneighbors;

            //////////////////////////////////
            // add the new neighbors.
            for (int i = 0; i < node.nneighbors; i++) {

                int n = node.neighbors[i];

                // don't add a duplicate.
                if (flags[n]==flag)
                    continue;

                neighbors[outidx++] = n;
                flags[n] = flag;
            }

            nneighbors = outidx;
        }

        // For each of our neighbors, add our current set of neighbors
        // and remove ourselves. (i.e., marginalize out this node).
        public final void marginalizeOut()
        {
            for (int i = 0; i < nneighbors; i++) {
                nodes[neighbors[i]].removeNodeAndAddNeighbors(this);
            }
        }
    }

    public MinimumDegreeOrderingRef()
    {
    }

    public int[] getPermutation(Matrix A)
    {
        int n = A.getRowDimension();
        int m = A.getColumnDimension();

        nodes = new Node[n];
        perm = new int[n];
        flags = null;
        nextFlag = 1;

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

        //////////////
        // Repeatedly pick the node with lowest degree.
        for (int i = 0; i < n; i++) {

            // find the best node.
            Node bestNode = null;
            int  bestj = -1;

            for (int j = 0; j < n; j++) {
                Node node = nodes[j];
                if (node.used)
                    continue;

                if (bestNode==null || node.size() < bestNode.size()) {
                    bestNode = node;
                    bestj = j;
                }
            }

            perm[i] = bestj;
            bestNode.used = true;

            // marginalize out this node, connecting all of its
            // neighbors.
            bestNode.marginalizeOut();
        }

        return perm;
    }
}
