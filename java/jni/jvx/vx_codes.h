#ifndef _VX_CODES_h
#define _VX_CODES_h

#define OP_PROGRAM            1
#define OP_ELEMENT_ARRAY      2
#define OP_VERT_ATTRIB        3
#define OP_VERT_ATTRIB_COUNT  4
#define OP_UNIFORM_COUNT      5
#define OP_UNIFORM_MATRIX_FV  6
#define OP_TEXTURE_COUNT      7
#define OP_TEXTURE            8
#define OP_MODEL_MATRIX_44    9
#define OP_PM_MAT_NAME        10
#define OP_VALIDATE_PROGRAM   11
#define OP_DRAW_ARRAY         12
#define OP_UNIFORM_VECTOR_FV  13
#define OP_LINE_WIDTH  14

#define VX_FLOAT_ARRAY  0
#define VX_BYTE_ARRAY   1
#define VX_INT_ARRAY    2

// Events
#define VX_MOUSE_MOVED   1
#define VX_MOUSE_BUTTON  2
#define VX_KEY_DOWN   1
#define VX_KEY_UP  2

#define VX_SHIFT_MASK    1
#define VX_CTRL_MASK     2
#define VX_WIN_MASK      4
#define VX_ALT_MASK      8
#define VX_CAPS_MASK    16
#define VX_NUM_MASK     32


// XXX should these GL types be defined here??? or included from GL.h?
// OpenGL Es 2.0 Types:
// GL_POINTS, GL_LINE_STRIP, GL_LINE_LOOP, GL_LINES,
// GL_TRIANGLE_STRIP, GL_TRIANGLE_FAN, and GL_TRIANGLES
//#define GL_POINTS          0x0000
//#define GL_LINES           0x0001
//#define GL_LINE_LOOP       0x0002
//#define GL_LINE_STRIP      0x0003
//#define GL_TRIANGLES       0x0004
//#define GL_TRIANGLE_STRIP  0x0005
//#define GL_TRIANGLE_FAN    0x0006


#endif
