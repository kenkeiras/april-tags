//uniform mat4 proj;

attribute vec4 position;
attribute vec4 color;

varying vec4 cls;

void main()
{
    // Transforming The Vertex
    gl_Position = position;//proj*position;
    cls = color;
}
