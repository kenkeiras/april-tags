package april.vx;


public class Vx
{
    public static final int OP_VERT_SHADER = 0, OP_FRAG_SHADER = 1,
        OP_ELEMENT_ARRAY = 2, OP_VERT_ATTRIB = 3, OP_VERT_ATTRIB_COUNT = 4;


/* Data types */
    public static final int GL_BYTE= 0x1400, GL_UNSIGNED_BYTE = 0x1401, GL_SHORT = 0x1402, GL_UNSIGNED_SHORT= 0x1403, GL_INT = 0x1404, GL_UNSIGNED_INT= 0x1405,
        GL_FLOAT = 0x1406, GL_2_BYTES = 0x1407, GL_3_BYTES = 0x1408, GL_4_BYTES = 0x1409, GL_DOUBLE= 0x140A;

    // OpenGL Es 2.0 Types:
    // GL_POINTS, GL_LINE_STRIP, GL_LINE_LOOP, GL_LINES,
    // GL_TRIANGLE_STRIP, GL_TRIANGLE_FAN, and GL_TRIANGLES
    public static final int GL_POINTS = 0x0000, GL_LINES = 0x0001,
        GL_LINE_LOOP = 0x0002, GL_LINE_STRIP = 0x0003, GL_TRIANGLES = 0x0004,
        GL_TRIANGLE_STRIP = 0x0005, GL_TRIANGLE_FAN = 0x0006;

}