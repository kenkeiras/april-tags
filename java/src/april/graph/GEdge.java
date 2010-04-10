package april.graph;

import april.util.*;
import java.io.*;

/** Abstract information relating the position of two GraphNodes. **/
public abstract class GEdge
{
    /** Which GraphNodes are related via this edge? **/
    public int a, b;

    /** What is the Chi^2 error of this edge, given the graph? **/
    public abstract double getChi2(Graph g);

    public abstract void write(StructureWriter outs) throws IOException;
    public abstract void read(StructureReader ins) throws IOException;

    /** how many degrees of freedom? **/
    public abstract int getDOF();

    /** Linearize the edge. If lin is null, a new linearization will
     * be allocated. Alternatively, a lin can be passed in from a
     * previous invocation of this edge, and will re-use the data
     * structures available. **/
    public abstract Linearization linearize(Graph g, Linearization lin);

    public abstract GEdge copy();
}
