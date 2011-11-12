package april.vis;

/**
 * Describes the appearance of a polygon.
 **/
public interface VisAbstractFillStyle
{
    public void bindFill(GL gl);
    public void unbindFill(GL gl);
}
