package april.graph;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;

import april.jmat.*;
import april.jmat.geom.*;
import april.util.*;

import lcm.lcm.*;

/** A location in the world whose position is related via GraphConstraints. **/
public abstract class GNode
{
    /** Current state of the graph node. NEVER NULL. **/
    public double state[];

    /** Initial value of the node. NEVER NULL. **/
    public double init[];

    /** Ground truth of the node, may be null **/
    public double truth[];

    public HashMap<String, Attribute> attributes;

    /** Additional information associated with a GraphNode, often
     * sensor data.
     **/
    public static class Attribute
    {
        public Object          o;
        public StructureCoder  coder;

        public Attribute(Object o, StructureCoder coder)
        {
            this.o = o;
            this.coder = coder;
        }
    }

    public GNode()
    {
    }

    /** What is the dimensionality of state? **/
    public abstract int getDOF();

    public abstract double[] toXyzRpy(double s[]);

    public void setAttribute(String s, Object o)
    {
        StructureCoder coder = null;
        if (o instanceof Integer)
            coder = new IntCoder();
        if (o instanceof String)
            coder = new StringCoder();
        if (o instanceof Long)
            coder = new LongCoder();
        if (o instanceof double[])
            coder = new DoublesCoder();
        if (o instanceof LCMEncodable)
            coder = new LCMCoder();
        setAttribute(s, o, coder);
    }

    public void setAttribute(String s, Object o, StructureCoder coder)
    {
        if (attributes == null)
            attributes = new HashMap<String, Attribute>();
        Attribute attr = new Attribute(o, coder);
        attributes.put(s, attr);
    }

    public Object getAttribute(String s)
    {
        if (attributes == null)
            return null;

        Attribute attr = attributes.get(s);
        if (attr == null)
            return null;

        return attr.o;
    }

    public void write(StructureWriter outs) throws IOException
    {
        outs.writeComment("state");
        outs.writeDoubles(state);

        outs.writeComment("");
        outs.writeComment("truth");
        outs.writeDoubles(truth);

        outs.writeComment("");
        outs.writeComment("initial value");
        outs.writeDoubles(init);

        outs.writeComment("num attributes");
        if (attributes == null) {
            outs.writeInt(0);
        } else {
            // count the attributes that actually have valid coders
            // and data. We won't serialize those that don't.
            int nonnullattributes = 0;
            for (String key : attributes.keySet())
                if (attributes.get(key).coder != null && attributes.get(key).o != null)
                    nonnullattributes++;

            outs.writeInt(nonnullattributes);

            int keyidx = -1;
            for (String key : attributes.keySet()) {
                Attribute attr = attributes.get(key);

                if (attr.coder == null || attr.o == null)
                    continue;

                keyidx++;

                outs.writeComment("attribute "+keyidx);
                outs.writeString(key);
                outs.writeString(attr.coder.getClass().getName());

                outs.blockBegin();
                attr.coder.write(outs, attr.o);
                outs.blockEnd();
            }
        }
    }

    public void read(StructureReader ins) throws IOException
    {
        state = ins.readDoubles();
        truth = ins.readDoubles();
        init = ins.readDoubles();

        int nattributes = ins.readInt();
        for (int i = 0; i < nattributes; i++) {

            String key = ins.readString();
            String codername = ins.readString();
            StructureCoder coder = (StructureCoder) ReflectUtil.createObject(codername);

            ins.blockBegin();
            Object o = coder.read(ins);
            ins.blockEnd();
            setAttribute(key, o, coder);
        }
    }

    public abstract GNode copy();

    /** Make a shallow copy of the attributes. **/
    public HashMap<String, Attribute> copyAttributes()
    {
        if (attributes == null)
            return null;

        return (HashMap<String, Attribute>) attributes.clone();
    }

}
