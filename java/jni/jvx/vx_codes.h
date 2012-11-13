#ifndef _VX_CODES_h
#define _VX_CODES_h

#define OP_PROGRAM            1
#define OP_ELEMENT_ARRAY      2
#define OP_VERT_ATTRIB        3
#define OP_VERT_ATTRIB_COUNT  4
#define OP_UNIFORM_COUNT      5
#define OP_UNIFORM_MATRIX_FV  6

#define VX_FLOAT_ARRAY  0
#define VX_BYTE_ARRAY   1
#define VX_INT_ARRAY    2

// XXX should these GL types be defined here??? or included from GL.h?
// OpenGL Es 2.0 Types:
// GL_POINTS, GL_LINE_STRIP, GL_LINE_LOOP, GL_LINES,
// GL_TRIANGLE_STRIP, GL_TRIANGLE_FAN, and GL_TRIANGLES
#define GL_POINTS          0x0000
#define GL_LINES           0x0001
#define GL_LINE_LOOP       0x0002
#define GL_LINE_STRIP      0x0003
#define GL_TRIANGLES       0x0004
#define GL_TRIANGLE_STRIP  0x0005
#define GL_TRIANGLE_FAN    0x0006


#endif
