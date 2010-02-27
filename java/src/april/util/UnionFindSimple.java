package april.util;

import java.util.*;

/** Implementation of disjoint set data structure that packs each
 * entry into a single 'long' for performance. *
 */
public final class UnionFindSimple
{
    int data[]; // alternating parent ids, rank, size.

    /** @param maxid The maximum node id that will be referenced. **/
    public UnionFindSimple(int maxid)
    {
        data = new int[maxid*3];

        for (int i = 0; i < maxid; i++) {
            // everyone is their own cluster of size 1
            data[3*i+0] = i;
            data[3*i+1] = 0;
            data[3*i+2] = 1;
        }
    }

    public int getSetSize(int id)
    {
        return data[3*getRepresentative(id)+2];
    }

    public int getRepresentative(int id)
    {
        // terminal case: a node is its own parent.
        if (data[3*id]==id)
            return id;

        // otherwise, recurse...
        int root = getRepresentative(data[3*id]);

        // short circuit the path.
        data[3*id] = root;

        return root;
    }

    /** returns the id of the merged node. **/
    public int connectNodes(int aid, int bid)
    {
        int aroot = getRepresentative(aid);
        int broot = getRepresentative(bid);

        if (aroot == broot)
            return aroot;

        int arank = data[3*aroot+1];
        int brank = data[3*broot+1];
        int asz = data[3*aroot+2];
        int bsz = data[3*broot+2];

        if (arank > brank) {
            data[3*broot] = aroot;
            data[3*aroot+2] += bsz;
            return aroot;
        } else if (brank > arank) {
            data[3*aroot] = broot;
            data[3*broot+2] += asz;
            return broot;
        } else {
            // arank = brank
            data[3*aroot] = broot;
            data[3*broot+1]++;
            data[3*broot+2] += asz;
            return broot;
        }
    }

    public static void main(String args[])
    {
        int nedges = 100000;
        int nnodes = 1000;

        UnionFindSimple uf = new UnionFindSimple(nnodes);

        ArrayList<int[]> edges = new ArrayList<int[]>();
        Random r = new Random();

        for (int i = 0; i < nedges; i++) {
            int a = r.nextInt(nnodes);
            int b = r.nextInt(nnodes);

            edges.add(new int[] {a, b});

            uf.connectNodes(a, b);
        }

        System.out.println("");

        for (int a = 0; a < nnodes; a++) {

            // construct set of all reachable nodes.
            HashSet<Integer> reachable = new HashSet<Integer>();
            reachable.add(a);

            while (true) {
                int size0 = reachable.size();

                for (int edge[] : edges) {
                    if (reachable.contains(edge[0])) {
                        reachable.add(edge[1]);
                    }
                    if (reachable.contains(edge[1])) {
                        reachable.add(edge[0]);
                    }
                }

                if (reachable.size() == size0)
                    break;
            }

            for (int b = 0; b < nnodes; b++) {
                if (reachable.contains(b))
                    assert(uf.getRepresentative(a)==uf.getRepresentative(b));
                else
                    assert(uf.getRepresentative(a)!=uf.getRepresentative(b));
            }

            assert (reachable.size() == uf.getSetSize(a));
        }
    }
}
