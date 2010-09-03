package april.util;

import java.util.*;
import april.jmat.*;

/**
 * Given a set of theta measurements, pick the "average" (approximately).
 *
 * More formally, given a set of orientations, we wish to identify a
 * "reference theta" such that the sum of the squared differences
 * between each theta and the reference theta is minimized. This can
 * be visualized: each theta (including the reference theta) can be
 * mapped onto the unit circle): we wish to minimize the distance
 * between the reference point and every other points by traveling
 * along the circumference of the unit circle.
 *
 * APPROXIMATE CHORD SOLUTION
 * --------------
 * This is hard, however, so instead of computing the distance along the
 * circumference, we compute the distance along the chord.
 *
 * This method is by ebolson@umich.edu, inspired by a similar problem
 * in Horn's "closed-form solution of absolute orientation using unit
 * quaternions".
 *
 * Let a be the set of input points, and R(a_i) represent a rotation
 * of point a_i around the origin:
 *
 * R(x) = [ cos(theta)*a_x - sin(theta)*a_y,] [ sin(theta)*a_x + cos(theta)*a_y ]
 *
 * The error is:
 *
 * X^2 = SUM ( R(a_i) - [1 0]' )' * (R(a_i) - [1 0]')
 *
 * = SUM R'R - 2[1 0]R(a) + [1 0][1 0]'
 *
 * Note that R'R is constant, because R and R' are orthogonal. (R'R =
 * I). Dropping constant terms:
 *
 * X^2 = SUM 2[1 0]R(a)
 *
 * Differentiating with respect to theta:
 *
 * dX^2/dtheta = SUM cos(theta)a_x - sin(theta)a_y = 0
 *
 * Collecting cos and sin terms:
 *
 * cos(theta) SUM a_x = sin(theta) SUM a_y
 *
 * e.g.,:
 *
 * theta = atan2( SUM a_y , SUM a_x )
 *
 * EXACT SOLUTION
 * ----------------
 * This solution runs in O(n log n).
 *
 * Let us suppose that all of the input angles are mapped to [-PI,
 * PI].

 * All the input points can be shifted to be within PI degrees of the
 * reference angle by adding a multiple of 2PI degrees. If all the
 * input angles are constrained to [-PI, PI], then we can find a
 * reference angle [-PI, 2PI] such that all input points are within PI
 * degrees by either adding 0 or exactly 2PI to individual input points.
 *
 * More so, the input points that we must add 2PI to are the M points
 * with the smallest theta, but we do not know M. This is necessary
 * when the correct reference angle is large: the smallest points will
 * be more than PI degrees away, so they need to be moved to the right
 * side of the reference angle.
 *
 * If we knew M, computing the reference angle is easy: it is simply
 * the average of the (possibly shifted) input points. Let x[i] be the
 * input point [-PI,PI] and y[i] be the possibly shifted version of
 * that point, y[i] = x[i] + 2PI if i < M, otherwise y[i] = x[i].
 *
 * r = reference angle = (1 / N) * SUM_i y[i]
 * error = SUM_i (y[i] - r)^2
 *
 * We simply search over each value of M (from 0 to N), and recompute
 * the error. Both the reference angle and error can be written in
 * terms of the first and second moments of y[i], which gives us the
 * following strategy:
 *
 * 1) Compute A1 and A2, the first and second moments of y[i],
 * assuming M = 0. (This is just the first and second moments of
 * x[i]). This involves iterating over each of the input points.
 *
 * 2) Considering the points in x[i] in sorted order, update A1 and A2
 * such that they reflect y[i] = x[i] + 2PI. Compute the new reference
 * theta and error after every point (an O(1) operation) and report
 * the theta whose error was the smallest.
 *
 * Total run time is O(N log N) due to the sort operation. The other
 * two passes are O(N). Memory usage is O(N), since all points must be
 * stored so they can be sorted.
 *
 * SUMMARY
 * -------
 *
 * method         runtime          memory         notes
 * --------------------------------------------------------
 * brute          O(2PI*N / eps)   O(N)           worst-case error is eps/2
 * exact          O(N log N)       O(N)
 * chord          O(N)             O(1)           minimizes squared chord length, not squared arc length.
 **/
public class AverageTheta
{
    public static double averageThetaChord(List<Double> thetas)
    {
        double M = 0, N = 0;
        for (double t : thetas) {
            double x0 = Math.cos(t);
            double y0 = Math.sin(t);
            M += y0;
            N += x0;
        }
        return Math.atan2(M, N);
    }

    public static double averageThetaOptimal(List<Double> thetas)
    {
        double x[] = new double[thetas.size()];
        int xpos = 0;
        for (double d : thetas)
            x[xpos++] = MathUtil.mod2pi(d); // map to [-PI, PI]

        Arrays.sort(x); // ascending numerical order.

        // compute first and second moments without adding 2PI
        double A1 = 0, A2 = 0;

        for (int i = 0; i < x.length; i++) {
            A1 += x[i];
            A2 += x[i]*x[i];
        }

        // now, go back through again, converting elements one-by-one
        // into their +2PI versions, recomputing the error and picking
        // the best one.

        // initialize with case where all points are the non-2PI
        // version.
        double bestError = Double.MAX_VALUE;
        double bestTheta = -1;

        // note: i=-1 iteration tests case where none are flipped.
        for (int i = -1; i < x.length; i++) {

            if (i >= 0) {
                // flip the i'th component into the +2PI version
                A1 += 2*Math.PI;
                // A2 += (x[i] + 2*Math.PI)^2 - x[i]*x[i]
                A2 += 4*Math.PI*x[i] + 4*Math.PI*Math.PI;
            }

            double theta = A1 / x.length;
            double error = A2 - 2*A1*theta + x.length*theta*theta;

            if (error < bestError) {
                bestError = error;
                bestTheta = theta;
            }
        }

        return MathUtil.mod2pi(bestTheta);
    }

    /** For a given theta, compute the MSE **/
    public static double computeMSE(List<Double> thetas, double theta)
    {
        double sqerr = 0;
        for (double t : thetas) {
            double err = MathUtil.mod2pi(t - theta);
            sqerr += err * err;
        }
        return sqerr / thetas.size();
    }

    /**
     * Trying every theta (with a step size of dtheta), find the theta that results in the smallest MSE.
     **/
    public static double averageThetaBruteForce(List<Double> thetas, double dtheta)
    {
        double bestmse = Double.MAX_VALUE;
        double besttheta = 0;

        for (double theta = 0; theta < 2 * Math.PI; theta += dtheta) {
            double mse = computeMSE(thetas, theta);

            if (mse < bestmse) {
                bestmse = mse;
                besttheta = theta;
            }
        }
        return MathUtil.mod2pi(besttheta);
    }

    /** Test harness... **/
    public static void main(String args[])
    {
        ArrayList<Double> thetas = new ArrayList<Double>();
        Random rand = new Random();
        double worstError = 0;
        double worstRatio = 0;

        for (int trial = 0; trial < 2000; trial++) {
            thetas.clear();
            double offset = MathUtil.mod2pi(2 * Math.PI * rand.nextDouble());

            double stddev = 2.0;

            // add contaminated samples
            for (int i = 0; i < 100; i++) {
                thetas.add(offset + stddev * (rand.nextGaussian()));
            }

            // add uniform noise everywhere
            for (int i = 0; i < 100; i++) {
                thetas.add(2 * Math.PI * rand.nextDouble());
            }

            double dumb = averageThetaBruteForce(thetas, Math.toRadians(0.1));
            double chord = averageThetaChord(thetas);
            double optimal = averageThetaOptimal(thetas);

            double dumbError = computeMSE(thetas, dumb);
            double chordError = computeMSE(thetas, chord);
            double optimalError = computeMSE(thetas, optimal);

            System.out.printf("Trial %d, Nominal offset: %15f:\n", trial, offset);
            System.out.printf("  Optimal    : %15f  (mse %15.10f)\n", optimal, optimalError);
            System.out.printf("  Brute force: %15f  (mse %15.10f)\n", dumb, dumbError);
            System.out.printf("  Chord      : %15f  (mse %15.10f)\n", chord, chordError);

            assert(optimalError < dumbError && optimalError < chordError);

            worstRatio = Math.max(worstRatio, chordError / optimalError);
        }

        System.out.println("worst error (ratio):   " + worstRatio);
    }
}
