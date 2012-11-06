package april.vx;


public class Vx
{
    public static final int OP_VERT_SHADER = 0, OP_FRAG_SHADER = 1,
        OP_ELEMENT_ARRAY = 2, OP_VERT_ATTRIB = 3;

    public static final int VX_FLOAT_ARRAY = 0, VX_BYTE_ARRAY = 1, VX_INT_ARRAY =2;

    // OpenGL Es 2.0 Types:
    // GL_POINTS, GL_LINE_STRIP, GL_LINE_LOOP, GL_LINES,
    // GL_TRIANGLE_STRIP, GL_TRIANGLE_FAN, and GL_TRIANGLES
    public static final int GL_POINTS = 0x0000, GL_LINES = 0x0001,
        GL_LINE_LOOP = 0x0002, GL_LINE_STRIP = 0x0003, GL_TRIANGLES = 0x0004,
        GL_TRIANGLE_STRIP = 0x0005, GL_TRIANGLE_FAN = 0x0006;

}