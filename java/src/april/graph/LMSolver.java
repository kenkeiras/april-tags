package april.graph;

import april.jmat.*;
import april.jmat.ordering.*;

public class LMSolver extends CholeskySolver
{
    double lambda = 1e-6;

    // Consistency Constructor Solver(Graph g)
    public LMSolver(Graph g)
    {
        this(g, new MinimumDegreeOrdering());
    }

    public LMSolver(Graph g, Ordering ordering)
    {
        super(g, ordering);
    }

    public double getDamping()
    {
        return lambda;
    }

    public void setDamping(double v)
    {
        this.lambda = v;
    }

    public void scaleDamping(double s)
    {
        this.lambda *= s;
    }

    @Override
    Matrix solveForX(Matrix PAP, Matrix PB)
    {
        Matrix lambdaI = Matrix.identity(PAP.getRowDimension(), PAP.getColumnDimension()).times(lambda);
        PAP.plusEquals(lambdaI);

        CholeskyDecomposition cd = new CholeskyDecomposition(PAP, verbose);
        L = cd.getL();
        return cd.solve(PB);
    }
}
