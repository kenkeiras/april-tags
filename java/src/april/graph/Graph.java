package april.graph;

import java.util.*;
import java.io.*;
import java.lang.reflect.*;

import april.jmat.*;
import april.jmat.geom.*;

import april.util.*;

/** A a collection of GNodes and GEdges. **/
public class Graph
{
    /** the set of all constraints. **/
    public ArrayList<GEdge> edges = new ArrayList<GEdge>();

    /** the set of all nodes. **/
    public ArrayList<GNode> nodes = new ArrayList<GNode>();

    /** Each node may have a different number of degrees of
        freedom. The stateIndices array allows us to rapidly lookup
        the state vector index corresponding to a particular
        node. (These are computed on-demand by getStateIndex().
    **/
    ArrayList<Integer> stateIndices = new ArrayList<Integer>();

    /** Chi^2 error and other error statistics. **/
    public static class ErrorStats
    {
        public double chi2;
        public double chi2normalized;
        public int degreesOfFreedom;

        public double meanSquaredDistanceError;
        public double meanSquaredThetaError;
    }

    public int getStateLength()
    {
        return getStateIndex(nodes.size()-1) + nodes.get(nodes.size()-1).getDOF();
    }

    public int getStateIndex(int nodeIndex)
    {
        for (int node = stateIndices.size(); node <= nodeIndex; node++) {
            if (node == 0) {
                stateIndices.add(0);
                continue;
            }

            int lastIndex = stateIndices.get(node-1);
            GNode gn = nodes.get(node - 1);
            stateIndices.add(lastIndex + gn.getDOF());
        }

        return (int) stateIndices.get(nodeIndex);
    }

    public void write(String path) throws IOException
    {
        FileWriter outs = new FileWriter(path);
        write(new BufferedWriter(outs));
        outs.close();
    }

    public void write(BufferedWriter _outs) throws IOException
    {
        StructureWriter outs = new TextStructureWriter(_outs);

        for (int i = 0; i < nodes.size(); i++) {
            GNode gn = nodes.get(i);

            outs.writeComment("node "+i);
            outs.writeString(gn.getClass().getName());
            outs.blockBegin();
            gn.write(outs);
            outs.blockEnd();
        }

        for (int i = 0; i < edges.size(); i++) {
            GEdge gc = edges.get(i);

            outs.writeComment("constraint "+i);
            outs.writeString(gc.getClass().getName());
            outs.blockBegin();
            gc.write(outs);
            outs.blockEnd();
        }

        outs.close();
        System.out.printf("wrote %d nodes and %d edges\n", nodes.size(), edges.size());
    }

    /** Returns { (xmin, ymin, zmin), (xmax, ymax, zmax) } **/
    public ArrayList<double[]> getBounds()
    {
        ArrayList<double[]> bounds = new ArrayList<double[]>();

        double xmin = Double.MAX_VALUE, xmax = -Double.MAX_VALUE;
        double ymin = Double.MAX_VALUE, ymax = -Double.MAX_VALUE;
        double zmin = Double.MAX_VALUE, zmax = -Double.MAX_VALUE;

        for (GNode gn : nodes) {
            double xytrpy[] = gn.toXyzRpy(gn.state);

            xmin = Math.min(xmin, xytrpy[0]);
            ymin = Math.min(ymin, xytrpy[1]);
            zmin = Math.min(zmin, xytrpy[2]);

            xmax = Math.max(xmax, xytrpy[0]);
            ymax = Math.max(ymax, xytrpy[1]);
            zmax = Math.max(zmax, xytrpy[2]);
        }

        bounds.add(new double[] {xmin, ymin, zmin});
        bounds.add(new double[] {xmax, ymax, zmax});

        return bounds;
    }

    public Graph()
    {
    }

    public Graph(String path) throws IOException
    {
        StructureReader ins = new TextStructureReader(new BufferedReader(new FileReader(path)));

        while (true) {
            String classname = ins.readString();

            if (classname == null) // EOF?
                break;

            Object obj = ReflectUtil.createObject(classname);

            if (obj instanceof GNode) {
                GNode gn = (GNode) obj;

                ins.blockBegin();
                gn.read(ins);
                nodes.add(gn);
                ins.blockEnd();

            } else if (obj instanceof GEdge) {
                GEdge ge = (GEdge) obj;
                ins.blockBegin();
                ge.read(ins);
                edges.add(ge);
                ins.blockEnd();
            } else {
                System.out.println("Unable to handle object of type: "+obj);
            }
        }

        System.out.printf("loaded %d nodes and %d edges\n", nodes.size(), edges.size());

        ins.close();
    }

    // Returns a deep copy of this graph with the exception of a
    // node's attributes.
    public Graph copy()
    {
        return copy(null);
    }

    // Copies the contents of this graph into graph g if g is null, a
    // new graph is created, otherwise, all containers in g are
    // emptied forcibly
    public Graph copy(Graph g)
    {
        if (g == null)
            g = new Graph();
        else {
            g.nodes.clear();
            g.edges.clear();
            g.stateIndices.clear();
        }

        for (GEdge edge : edges)
            g.edges.add(edge.copy());
        for (GNode node: nodes)
            g.nodes.add(node.copy());
        for (Integer index : stateIndices)
            g.stateIndices.add(index);

        return g;
    }

    // Copies the entire node state from graph g into this one
    // Use this method when copying the graph to perform thread safe optimization
    // NOTE: it must be ensured externally that the ith node in each graph
    // is of identical type (e.g. GXYTNode)
    public void setNodeState(Graph g)
    {
        assert(nodes.size() >= g.nodes.size());

        for (int i = 0; i < g.nodes.size(); i++) {
            GNode src  = g.nodes.get(i);
            GNode dest = nodes.get(i);
            System.arraycopy(src.state, 0, dest.state, 0, src.state.length);
        }
    }

    public ErrorStats getErrorStats()
    {
        ErrorStats estats = new ErrorStats();

        int stateDOF = 0;
        int edgeDOF = 0;

        for (GEdge edge: edges) {
            estats.chi2 += edge.getChi2(this);
            edgeDOF += edge.getDOF();
        }

        for (GNode node: nodes) {
            stateDOF += node.getDOF();
        }

        estats.chi2normalized = estats.chi2 / (edgeDOF - stateDOF);

        ArrayList<double[]> xs = new ArrayList<double[]>();
        ArrayList<double[]> ys = new ArrayList<double[]>();

        for (GNode n : nodes) {
            if (n.truth != null) {
                xs.add(n.state);
                ys.add(n.truth);
            }
        }

        double t[] = AlignPoints2D.align(xs, ys);

        ArrayList<double[]> xts = LinAlg.transform(t, xs);

        double xyDist2 = 0;
        double thetaDist2 = 0;

        for (int i = 0; i < xts.size(); i++) {
            double x[] = xts.get(i);
            double y[] = ys.get(i);

            double xyDistSq = LinAlg.squaredDistance(x, y, 2);
            xyDist2 += xyDistSq;

            if (x.length == 3) {
                double thetaErr = MathUtil.mod2pi(y[2] - x[2] - t[2]);
                thetaDist2 += thetaErr*thetaErr;
            }
        }

        estats.meanSquaredDistanceError = xyDist2 / xs.size();
        estats.meanSquaredThetaError = thetaDist2 / xs.size();

        return estats;
    }
}
