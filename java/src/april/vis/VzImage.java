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

    public static final int FLIP = 1;

    // Convenience constructor. Maps pixels directly to images, so camera images will appear upside down
    // suggested usage for rightsideup images:
    //  vb.addBack(new VisChain(LinAlg.scale(1,-1,1),new VzImage(cameraImage)));
    public VzImage(BufferedImage im)
    {
        this(im, 0);
    }

    public VzImage(BufferedImage im, int flags)
    {
        this(new VisTexture(im), flags);
    }

    public VzImage(VisTexture texture, int flags)
    {
        this.texture = texture;
        this.vertices = new double[][] { {0, 0, 0},
                                         {texture.getWidth(), 0, 0},
                                         {texture.getWidth(), texture.getHeight(), 0},
                                         {0, texture.getHeight(), 0} };

        this.texcoords = new double[][] { { 0, 0 },
                                          { 1, 0 },
                                          { 1, 1 },
                                          { 0, 1 } };

        if ((flags & FLIP) != 0) {
            for (int i = 0; i < texcoords.length; i++)
                texcoords[i][1] = 1 - texcoords[i][1];
        }

        this.c = null;
    }

    /** Display an image. Can pass 'null' for color if texture is not alpha mask
     *  @param vertices A list of four 2D or 3D vertices.
     *  @param texcoords A list of four 2D texture coordinates, normalized between 0 and 1.
     *  @param c a color to modulate the texture with. Use white to make image appear normally.
     **/
    public VzImage(VisTexture texture, double vertices[][], double texcoords[][], Color c)
    {
        this.texture = texture;
        this.vertices = LinAlg.copy(vertices);
        this.texcoords = LinAlg.copy(texcoords);
        this.c = c;

        assert(vertices.length == 4);
        assert(texcoords.length == 4);

        for (int i = 0; i < 4; i++) {
            if (vertices[i].length < 3) {
                vertices[i] = new double[] { vertices[i][0], vertices[i][1], 0 };
            }
        }
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        if (c != null)
            gl.glColor(c);
        else
            gl.glColor(Color.white);

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
