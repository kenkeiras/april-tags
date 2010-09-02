package april.vis;

import april.jmat.geom.*;
import april.jmat.*;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;
import java.awt.*;
import java.util.*;
import java.io.*;

import lcm.lcm.*;

/** Perform a series of cumulative Matrix transformations and object
 * draws. You can pass these in any order, in any combination.
 *
 * You can pass in Matrix, double[][], or double quat[] + double pos[] for a transformation.
 * You can pass in a VisObject to render something.
 **/
public class VisChain implements VisObject, VisSerializable
{

    ArrayList<Object> operations = new ArrayList<Object>();


    public VisChain()
    {
    }

    public VisChain(Object ... os)
    {
        add(os);
    }

    // this method must be added to disabiguate between a
    // two-dimensional array being interpreted as a varargs call
    // consisting of several one-dimensional doubles.
    public void add(double M[][])
    {
        operations.add(new Matrix(M));
    }

    public void add(Object ... os)
    {
        int i = 0;

        while (i < os.length) {
            if (os[i] == null) {
                i++;
                continue;
            }

            if (os[i] instanceof double[]) {

                double tmp[] = (double[]) os[i];
                if (tmp.length==6) {
                    operations.add(new Matrix(LinAlg.xyzrpyToMatrix(tmp)));
                    i++;
                } else {
                    assert(i+1 < os.length);
                    double q[] = (double[]) os[i];
                    double p[] = (double[]) os[i+1];

                    double T[][] = LinAlg.quatPosToMatrix(q, p);
                    operations.add(new Matrix(T));
                    i+=2;
                }
                continue;
            }

            if (os[i] instanceof double[][]) {
                operations.add(new Matrix((double[][]) os[i]));
                i++;
                continue;
            }

            if (os[i] instanceof Matrix) {
                Matrix T = (Matrix) os[i];
                operations.add(T);
                i++;
                continue;
            }

            if (os[i] instanceof VisObject) {
                operations.add((VisObject) os[i]);
                i++;
                continue;
            }

            // unknown type!
            System.out.println("VisChain: Unknown object added to chain: "+os[i]);
            assert(false);
            i++;
        }
    }

    public void render(VisContext vc, GL gl, GLU glu)
    {
        for (Object o : operations) {

            if (o instanceof Matrix) {
                VisUtil.multiplyMatrix(gl, (Matrix) o);
                continue;
            }

            if (o instanceof VisObject) {
                VisUtil.pushGLState(gl);
                VisObject vo = (VisObject) o;
                vo.render(vc, gl, glu);
                VisUtil.popGLState(gl);
            }
        }
    }

    public void serialize(LCMDataOutputStream out) throws IOException
    {
        for (Object o : operations) {
            if (o instanceof Matrix) {
                out.writeStringZ("Matrix");
                double mat[][] = ((Matrix)o).copyArray();
                out.writeInt(mat.length);
                out.writeInt(mat.length > 0 ? mat[0].length : 0);
                if (mat.length == 0)
                    continue;
                for (int i =0; i < mat.length; i++) {
                    for (int j = 0; j < mat[0].length; j++) {
                        out.writeDouble(mat[i][j]);
                    }
                }
            } else if (o instanceof VisSerializable) {
                out.writeStringZ("VisSerializable");
                VisSerialize.serialize((VisSerializable)o, out);
            }

        }

    }

    public void unserialize(LCMDataInputStream in) throws IOException
    {
        while (in.available() != 0) {
            String type = in.readStringZ();
            if (type.equals("Matrix")) {
                int rows = in.readInt();
                int cols = in.readInt();
                double mat[][] = new double[rows][cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        mat[r][c] = in.readDouble();
                    }
                }
                operations.add(new Matrix(mat));

            } else if (type.equals("VisSerializable")) {

                VisSerializable obj =  VisSerialize.unserialize(in);
                if (obj != null)
                    operations.add((VisObject)obj);
            } else {
                assert(false);
            }
        }

    }

}
