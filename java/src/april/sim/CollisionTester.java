package april.sim;

public interface CollisionTester
{
    /** Does a circle positioned at xy with radius 'radius' collide
     * with something?
     **/
    public boolean isCollision(double x, double y, double radius);
}
