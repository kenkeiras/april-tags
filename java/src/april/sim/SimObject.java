package april.sim;

import april.vis.*;

import java.io.*;

// An object in our world model
public interface SimObject
{
    /** If starting at a vector xyz and traveling in unit direction
     * dir, when will the first collision occur? Return less than zero
     * if there is no collision.
     **/
    public double collisionRay(double p[], double dir[]);

    /** What is the minimum-sized sphere that collides with the object? **/
    public double collisionSphere(double p[]);

    public VisObject getVisObject();

    /** Restore state that was previously written **/
    public void read(BufferedReader ins) throws IOException;

    /** Write one or more lines that serialize this instance. No line
     * is allowed to consist of just an asterisk. **/
    public void write(BufferedWriter outs) throws IOException;
}
