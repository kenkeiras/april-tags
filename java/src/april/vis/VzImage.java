package april.vis;

import java.awt.*;
import java.awt.image.*;

import april.jmat.*;

public class VzImage implements VisObject
{
    VisTexture texture;
    double vertices[][];
    double texcoords[][];
    Color c = Color.gray;

    // Convenience constructor. Maps pixels directly to images, so camera images will appear upside down
    // suggested usage for rightsideup images:
    //  vb.addBack(new VisChain(LinAlg.scale(1,-1,1),new VzImage(cameraImage)));
    public VzImage(BufferedImage im)
    {
        this.texture = new VisTexture(im,false);
        this.vertices = new double[][]{{0,0},{im.getWidth(),0},
                                       {im.getWidth(),im.getHeight()},{0,im.getHeight()}};
        this.texcoords = LinAlg.copy(vertices);
        this.c = null;
    }

    // Can pass 'null' for color if texture is not alpha mask
    public VzImage(VisTexture texture, double vertices[][], double texcoords[][], Color c)
    {
        this.texture = texture;
        this.vertices = LinAlg.copy(vertices);
        this.texcoords = LinAlg.copy(texcoords);
        this.c = c;

        assert(vertices.length == 4);
        assert(texcoords.length == 4);
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        if (c != null)
            gl.glColor(c);

        texture.bind(gl);

        gl.glBegin(gl.GL_QUADS);

        for (int i = 0; i < 4; i++) {
            gl.glTexCoord2d(texcoords[i][0], texcoords[i][1]);
            gl.glVertex3d(vertices[i][0], vertices[i][1], vertices[i][2]);
        }

        gl.glEnd();

        texture.unbind(gl);
    }
}
