package april.graph;

import java.util.*;

import april.jmat.*;
import april.util.*;

/** Given a graph and a "reference node", search for the shortest
 * (least uncertain) path from the reference node to all other nodes.
 * This is often a fair approximation to the optimal graph, but can
 * be computed very quickly.
 *
 * The approximation is poor only when a path segment has a number of
 * equal-variance links in parallel, with no low-variance links. In
 * this case, the variance should decrease as 1/N with N parallel
 * links.
 *
 * This is implemented as a breadth-first search, which is in fact
 * optimal.
 **/

public class DijkstraProjection
{
    int refpose;

    // The best rigid body constraint to each node in the graph from refpos
    ArrayList<GXYTEdge> projection;

    // For each node in the graph, all of the rigid body constraints
    // that lead away from it.
    ArrayList<ArrayList<GXYTEdge>> nodeConstraints;

    // we won't follow these edges.
    HashSet<GEdge> forbiddenConstraints;

    // We'll stop when we've found paths to all of these nodes. (if
    // null, all nodes are processed.)
    HashSet<Integer> neededNodes;

    MaxHeap<GXYTEdge> heap;

    public DijkstraProjection(Graph g, int refpose)
    {
        this(g, refpose, null, null);
    }

    /** neededNodes will be modified! **/
    public DijkstraProjection(Graph g, int refpose,
                              HashSet<GEdge> forbiddenConstraints,
                              HashSet<Integer> neededNodes)
    {
        this.refpose = refpose;
        this.forbiddenConstraints = forbiddenConstraints;
        this.neededNodes = neededNodes;

        ///////////////////////////////////////////////////////////
        // Build a table of constraints for each node.
        // We'll use this for the breadth-first search
        nodeConstraints = new ArrayList<ArrayList<GXYTEdge>>();
        while (nodeConstraints.size() < g.nodes.size())
            nodeConstraints.add(new ArrayList<GXYTEdge>());

        for (GEdge _gc : g.edges) {
            if (_gc instanceof GXYTEdge) {
                GXYTEdge gc = (GXYTEdge) _gc;
                nodeConstraints.get(gc.nodes[0]).add(gc);
                nodeConstraints.get(gc.nodes[1]).add(gc);
            }
        }

        ///////////////////////////////////////////////////////////
        // allocate the projection datastructure
        projection = new ArrayList<GXYTEdge>();
        while (projection.size() < g.nodes.size())
            projection.add(null);

        heap = new MaxHeap<GXYTEdge>(g.nodes.size());

        ///////////////////////////////////////////////////////////
        // Initialize the search by expanding the first node
        GXYTEdge initialConstraint = new GXYTEdge();
        initialConstraint.z = new double[3];
        initialConstraint.truth = new double[3];
        initialConstraint.P = new double[3][3];

        projection.set(refpose, initialConstraint);
        if (neededNodes != null)
            neededNodes.remove(refpose);

        expand(refpose);

        ///////////////////////////////////////////////////////////
        // keep searching.
        while (heap.size() > 0) {
            GXYTEdge gc = heap.removeMax();

            // every constraint must be with respect to the reference pose...
            assert(gc.nodes[0] == refpose);

            // already have a (better) projection for this node?
            // if so, skip this one.
            if (projection.get(gc.nodes[1])!=null)
                continue;

            // this constraint is a keeper, let's expand from here.
            projection.set(gc.nodes[1], gc);

            if (neededNodes != null) {
                neededNodes.remove(gc.nodes[1]);
                if (neededNodes.size() == 0)
                    return;
            }

            expand(gc.nodes[1]);
        }

        // clean up to help the garbage collector
        heap = null;
        nodeConstraints = null;
    }

    void expand(int pose)
    {
        ArrayList<GXYTEdge> gcs = nodeConstraints.get(pose);

        GXYTEdge current = projection.get(pose);

        for (GXYTEdge gc : gcs) {

            if (forbiddenConstraints!=null && forbiddenConstraints.contains(gc))
                continue;

            GXYTEdge followgc = null;

            if (gc.nodes[0] == pose) {
                followgc = gc;
            } else {
                assert(gc.nodes[1] == pose);
                followgc = gc.invert();
            }

            // don't need this edge, skip it.
            if (projection.get(followgc.nodes[1])!=null)
                continue;

            GXYTEdge newEdge = current.compose(followgc);
            heap.add(newEdge, 1.0/(1.0 + LinAlg.det(newEdge.P)));
        }
    }

    public GXYTEdge getConstraint(int node)
    {
        return projection.get(node);
    }
}
