package april.vis;

public interface VisAbstractVertexData
{
    public void bindVertex(GL gl);
    public void unbindVertex(GL gl);

    /** returns the number of vertices. **/
    public int size();
}
