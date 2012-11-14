package april.vx;

import java.util.*;
import april.jmat.*;

public class MatrixStack
{
    ArrayList<double[][]> stack = new ArrayList();

    double M[][];
    final int dim = 4;

    public void pushMatrix()
    {
        stack.add(LinAlg.copy(M));
    }

    public void loadIdentity()
    {
        M = LinAlg.identity(dim);
    }

    public void multMatrix(double B[][])
    {
        LinAlg.timesEquals(M,B);
    }

    public double[][] getMatrix()
    {
        return LinAlg.copy(M);
    }

    public void popMatrix()
    {
        M = stack.remove(stack.size()-1);
    }

}