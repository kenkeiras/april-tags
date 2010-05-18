package april.vis;

import javax.media.opengl.GL;
import javax.media.opengl.GLException;

public class VisDisplayList
{
    GL gl;
    int id;
    
    public VisDisplayList(GL gl)
    {
        this.gl = gl;
        id = gl.glGenLists(1);
        
        if (id == GL.GL_INVALID_VALUE)
            throw new GLException("Cannot create OpenGL display list");
        else if (id == GL.GL_INVALID_OPERATION)
            throw new GLException("List creation cannot occur between glBegin() and glEnd()");

    }
    
    public void begin()
    {
        gl.glNewList(id, GL.GL_COMPILE);
    }
    
    public void end()
    {
        gl.glEndList();
    }
    
    public void display()
    {
        gl.glCallList(id);
    }

    @Override
    protected void finalize() throws Throwable
    {
        gl.glDeleteLists(id, 1);
    }
}
