package april.sim;

import april.vis.*;
import april.util.*;

import java.io.*;

// An object in our world model
public interface SimObject
{
    // SimObjects must have a creator taking a SimWorld object.
    // SimObject(SimWorld sw);

    /** Where is the object? (4x4 matrix) **/
    public double[][] getPose();
    public void setPose(double T[][]);

    /** What is the shape of this object? (Relative to the origin)**/
    public Shape getShape();

    /** What does the object LOOK like? (should be drawn at the
     * origin).
     **/
    public VisObject getVisObject();

    /** Restore state that was previously written **/
    public void read(StructureReader ins) throws IOException;

    /** Write one or more lines that serialize this instance. No line
     * is allowed to consist of just an asterisk. **/
    public void write(StructureWriter outs) throws IOException;
}
