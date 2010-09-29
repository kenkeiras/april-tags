package april.sim;

import april.jmat.*;

public class Collisions
{
    public static boolean collision(Shape _sa, Shape _sb)
    {
        double T[][] = LinAlg.identity(4);
        return collision(_sa, T, _sb, T);
    }

    public static boolean collision(Shape _sa, double Ta[][], Shape _sb, double Tb[][])
    {
        if (_sa instanceof CompoundShape) {
            return collision((CompoundShape) _sa, Ta, _sb, Tb);
        }
        if (_sb instanceof CompoundShape) {
            return collision((CompoundShape) _sb, Tb, _sa, Ta);
        }
        if (_sa instanceof BoxShape && _sb instanceof BoxShape) {
            return collision((BoxShape) _sa, Ta, (BoxShape) _sb, Tb);
        }
        if (_sa instanceof SphereShape && _sb instanceof SphereShape) {
            return collision((SphereShape) _sa, Ta, (SphereShape) _sb, Tb);
        }
        if (_sa instanceof BoxShape && _sb instanceof SphereShape) {
            return collision((BoxShape) _sa, Ta, (SphereShape) _sb, Tb);
        }
        if (_sb instanceof BoxShape && _sa instanceof SphereShape) {
            return collision((BoxShape) _sb, Tb, (SphereShape) _sa, Ta);
        }
        assert(false);
        return false;
    }

    public static boolean collision(CompoundShape sa, double Ta[][], Shape _sb, double Tb[][])
    {
        for (Object op : sa.ops) {
            if (op instanceof double[][]) {
                Ta = LinAlg.matrixAB(Ta, (double[][]) op);
            } else if (op instanceof Shape) {
                if (Collisions.collision((Shape) op, Ta, _sb, Tb))
                    return true;
            } else {
                System.out.println(op);
                assert(false);
            }
        }
        return false;
    }

    public static boolean collision(BoxShape sa, double Ta[][], SphereShape sb, double Tb[][])
    {
        // TODO: fast check first?

        // project the sphere's center into the box's coordinate system.
        double T[][] = LinAlg.matrixAB(LinAlg.inverse(Ta), Tb);

        // how far away are we?
        double ex = Math.max(0, Math.abs(T[0][3]) - sa.sxyz[0]/2);
        double ey = Math.max(0, Math.abs(T[1][3]) - sa.sxyz[1]/2);
        double ez = Math.max(0, Math.abs(T[2][3]) - sa.sxyz[2]/2);

        double e2 = ex*ex + ey*ey + ez*ez;

        return e2 < sb.r*sb.r;
    }

    public static boolean collision(SphereShape sa, double Ta[][], SphereShape sb, double Tb[][])
    {
        double d = Math.sqrt(LinAlg.sq(Ta[0][3]-Tb[0][3]) +
                             LinAlg.sq(Ta[1][3]-Tb[1][3]) +
                             LinAlg.sq(Ta[2][3]-Tb[2][3]));

        return (d - sa.r - sb.r) <= 0;
    }

    public static boolean collision(BoxShape sa, double Ta[][], BoxShape sb, double Tb[][])
    {
        // TODO: fast check first?

        BoxShape sa2 = sa.transform(Ta);
        BoxShape sb2 = sb.transform(Tb);

        if (LinAlg.faceBelowPoints(sa2.planes, sb2.vertices) ||
            LinAlg.faceBelowPoints(sb2.planes, sa2.vertices))
            return false;

        return true;
    }

    public static double collisionDistance(double pos[], double dir[], Shape _s, double T[][])
    {
        if (_s instanceof SphereShape)
            return collisionDistance(pos, dir, (SphereShape) _s, T);
        if (_s instanceof BoxShape)
            return collisionDistance(pos, dir, (BoxShape) _s, T);
        if (_s instanceof CompoundShape)
            return collisionDistance(pos, dir, (CompoundShape) _s, T);

        assert(false);
        return Double.MAX_VALUE;
    }

    public static double collisionDistance(double pos[], double dir[], SphereShape s, double T[][])
    {
        // unit vector from pos to center of sphere
        double xyz[] = new double[] { T[0][3], T[1][3], T[2][3] };
        double e[] = LinAlg.normalize(LinAlg.subtract(xyz, pos));

        // cosine of theta between dir and vector that would lead to
        // center of circle
        double costheta = LinAlg.dotProduct(e, dir);

        // law of cosines gives us:
        // r^2 = x^2 + d^2 - 2xd cos(theta)
        double d = LinAlg.distance(xyz, pos);

        // solve for x using quadratic formula
        double A = 1;
        double B = -2*d*costheta;
        double C = d*d - s.r*s.r;

        // no collision
        if (B*B - 4*A*C < 0)
            return Double.MAX_VALUE;

        double x1 = (-B - Math.sqrt(B*B - 4 * A * C)) / (2*A);
        if (x1 >= 0)
            return x1;

        double x2 = (-B + Math.sqrt(B*B - 4 * A * C)) / (2*A);
        if (x2 >= 0)
            return x2;

        return Double.MAX_VALUE;
    }

    public static double collisionDistance(double pos[], double dir[], CompoundShape s, double T[][])
    {
        double d = Double.MAX_VALUE;

        for (Object op : s.ops) {
            if (op instanceof double[][]) {
                T = LinAlg.matrixAB(T, (double[][]) op);
            } else if (op instanceof Shape) {
                d = Math.min(d, collisionDistance(pos, dir, (Shape) op, T));
            } else {
                System.out.println(op);
                assert(false);
            }
        }
        return d;
    }

    public static double collisionDistance(double pos[], double dir[], BoxShape s, double T[][])
    {
        double Tinv[][] = LinAlg.inverse(T);

        pos = LinAlg.transform(Tinv, pos);
        dir = LinAlg.transformRotateOnly(Tinv, dir);

        return LinAlg.rayCollisionBox(pos, dir, s.sxyz);
    }

    public static void main(String args[])
    {
        double p[] = new double[] { 1, 0, 0, -1 };
        LinAlg.print(LinAlg.transformPlane(LinAlg.matrixAB(LinAlg.translate(1, 0, 0),
                                                           LinAlg.rotateZ(Math.PI/4)), p));

        CompoundShape a = new CompoundShape(LinAlg.translate(5, 0, 0),
                                            new BoxShape(new double[] { 2, 2, 2}));

        CompoundShape b = new CompoundShape(LinAlg.translate(7.2, 0, 0),
                                            LinAlg.rotateZ(Math.PI/4),
                                            new BoxShape(new double[] { 10, .1, .1}));

        System.out.println(Collisions.collision(a, LinAlg.identity(4), b, LinAlg.identity(4)));
    }
}
