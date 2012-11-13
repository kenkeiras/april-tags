uniform mat4 proj;

attribute vec4 position;
attribute vec4 color;

varying vec4 cls;

void main()
{
    // Transforming The Vertex
    gl_Position = proj*position;//gl_Vertex;//gl_ModelViewProjectionMatrix * gl_Vertex;
    cls = color;
}
