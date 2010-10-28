package april.laser.scanmatcher;

import april.jmat.*;

class Chi2Data
{
    // compute a lower bound on the chi2 error for any point
    // within this search window.
    //
    // idea:
    //
    // 1.we factor the joint distribution P(x,y,t) into
    // P(x,y|t)P(t), since knowledge of t will influence the
    // cost within this window. But P(t) is constant within
    // this window, which makes our lives easier. We can
    // compute P(x,y|t) as though we made a noiseless
    // observation of t. P(t) is just P(x,y,t) dropping the x
    // & y variables.
    //
    // 2. We compute a bounding circle of our search
    // window. Suppose this circle has radius r and center
    // c. We will find some point p in this circle that has
    // the minimum chi2.
    //
    // 3. We compute the dominant (u1) and sub-dominant
    // (u2) eigenvectors of P(x,y|t).
    //
    // 4. Compute the residual e = c - mean(P(x,y|t)).
    //
    // 5. Reduce the residual by moving point c up to a total
    // distance 'r', first in the direction of u2, then in the
    // direction of u1.
    //
    // 6. Report the chi2 of the adjusted point c wrt
    // P(x,y|t), then multiply by P(t).
    //
    // Note: we can precompute (for each t) almost everything:
    // P(x,y|t), P(t), u1, u2.

    double t;
    double terr;
    double tchi2; // chi2 error of t, using just P(t)

    double u[];   // mean of P(x,y | t)  (2x1)
    double P[][]; // covariance of P(x,y | t) (2x2)
    double Pinv[][]; // inverse of P

    double v1[];  // dominant eigenvector of P (2x1)
    double v2[];  // sub-dominant eigenvector of P(2x1)

    double priorP[][];
    double priorPinv[][];

    public Chi2Data(double prior[], double priorP[][], double t)
    {
        this.t = t;
        this.priorP = priorP;
        this.priorPinv = LinAlg.inverse(priorP);

        this.terr = MathUtil.mod2pi(t - prior[2]);
        this.tchi2 = terr*priorPinv[2][2]*terr;

        // consider priorP consistig of 4 parts:
        // [ A   B ]   where A=2x2, B=2x1, C=1x2
        // [ B'  C ]
        //
        // u' = u + B*inv(C)*(t-u_t)
        this.u = new double[] { prior[0] + priorP[0][2]/priorP[2][2]*terr,
                                prior[1] + priorP[1][2]/priorP[2][2]*terr };

        double B[] = new double[] { priorP[2][0], priorP[2][1] };
        double C = priorP[2][2];

        // P = A - B*inv(C)*B'
        this.P = new double[][] { { priorP[0][0] - B[0]*B[0]/C, priorP[0][1] - B[0]*B[1]/C },
                                  { priorP[1][0] - B[1]*B[0]/C, priorP[1][1] - B[1]*B[1]/C } };
        this.Pinv = LinAlg.inverse(this.P);

        // find dominant direction via SVD
        double phi = 0.5*Math.atan2(-2*this.P[0][1],(this.P[1][1] - this.P[0][0]));
        double rho = phi + Math.PI / 2.0;

        this.v1 = new double[] { Math.cos(phi), Math.sin(phi) };
        this.v2 = new double[] { Math.cos(rho), Math.sin(rho) };
    }

    // Compute a lower bound of the chi^2 error at position (tx,ty,t)
    // within a circle of radius r.
    public double computeChi2(double tx, double ty, double r)
    {
        // compute error vector WRT P(x,y | t)
        double ex = u[0] - tx;
        double ey = u[1] - ty;

        double v2dot = v2[0]*ex + v2[1]*ey;
        double v2dist = v2dot*Math.min(Math.abs(v2dot), r)/Math.abs(v2dot);
        r -= v2dist;
        ex -= v2dist*v2[0];
        ey -= v2dist*v2[1];

        double v1dot = v1[0]*ex + v1[1]*ey;
        double v1dist = v1dot*Math.min(Math.abs(v1dot), r)/Math.abs(v1dot);
        ex -= v1dist*v1[0];
        ey -= v1dist*v1[1];

        double chi2 = ex*ex*Pinv[0][0] + 2*ex*ey*Pinv[0][1] + ey*ey*Pinv[1][1] + tchi2;

        if (false) {
            double naivechi2 = ex*ex*priorPinv[0][0] + 2*ex*ey*priorPinv[0][1] + 2*ex*terr*priorPinv[0][2] +
                ey*ey*priorPinv[1][1] + ey*terr*priorPinv[1][2] +
                terr*terr*priorPinv[2][2];
            System.out.printf("naive: %15f, ours: %15f\n", naivechi2, chi2);
        }

        return chi2;
    }
}

