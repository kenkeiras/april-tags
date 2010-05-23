package april.graph;

import april.jmat.*;
import april.util.*;

import java.util.*;

public class GaussSeidelSolver implements GraphSolver
{
    Graph g;
    int preprocessedEdges;
    ArrayList<ArrayList<Integer>> nodeEdges = new ArrayList<ArrayList<Integer>>();
    ArrayList<Linearization> linearizations = new ArrayList<Linearization>();

    DoublesCache cache = new DoublesCache(8, 4);

    public GaussSeidelSolver(Graph g)
    {
        this.g = g;
    }

    public boolean canIterate()
    {
        return true;
    }

    public void iterate()
    {
        while (nodeEdges.size() < g.nodes.size()) {
            nodeEdges.add(new ArrayList<Integer>());
        }

        while (preprocessedEdges < g.edges.size()) {
            GEdge ge = g.edges.get(preprocessedEdges);
            nodeEdges.get(ge.a).add(preprocessedEdges);
            nodeEdges.get(ge.b).add(preprocessedEdges);

            linearizations.add(g.edges.get(preprocessedEdges).linearize(g, null));

            preprocessedEdges++;
        }

        Tic tic = new Tic();

        for (int i = 0; i < g.nodes.size(); i++) {
            relaxNode(i);
        }
    }

    void relaxNode(int i)
    {
        GNode gn = g.nodes.get(i);
        int sz = gn.state.length;

        ArrayList<Integer> edges = nodeEdges.get(i);
        if (edges.size() == 0) {
            return;
        }

        double JTWJ[][]     = cache.get(sz, sz);
        LinAlg.clear(JTWJ);
        double JTWR[]       = cache.get(sz);
        LinAlg.clear(JTWR);

        double thisJTWJ[][] = cache.get(sz, sz);
        double thisJTWR[]   = cache.get(sz);

        for (int edgeidx : edges) {
            GEdge ge = g.edges.get(edgeidx);

            Linearization lin = ge.linearize(g, linearizations.get(edgeidx));

            if (ge.a == i) {
                double thisJTW[][] = cache.get(lin.Ja[0].length, lin.W[0].length);
                LinAlg.matrixAtB(lin.Ja, lin.W, thisJTW);

                LinAlg.matrixAB(thisJTW, lin.Ja, thisJTWJ);
                LinAlg.plusEquals(JTWJ, thisJTWJ);

                LinAlg.matrixAB(thisJTW, lin.R, thisJTWR);
                LinAlg.plusEquals(JTWR, thisJTWR);
                cache.put(thisJTW);
            } else {
                // ge.b == i
                double thisJTW[][] = cache.get(lin.Jb[0].length, lin.W[0].length);
                LinAlg.matrixAtB(lin.Jb, lin.W, thisJTW);

                LinAlg.matrixAB(thisJTW, lin.Jb, thisJTWJ);
                LinAlg.plusEquals(JTWJ, thisJTWJ);

                LinAlg.matrixAB(thisJTW, lin.R, thisJTWR);
                LinAlg.plusEquals(JTWR, thisJTWR);
                cache.put(thisJTW);
            }
        }

        double invJTWJ[][] = cache.get(sz, sz);
        double dx[] = cache.get(sz);

        LinAlg.inverse(JTWJ, invJTWJ);
        LinAlg.matrixAB(invJTWJ, JTWR, dx);
        LinAlg.minusEquals(gn.state, dx);

        cache.put(invJTWJ);
        cache.put(dx);
        cache.put(thisJTWJ);
        cache.put(thisJTWR);
        cache.put(JTWJ);
        cache.put(JTWR);
    }
}
