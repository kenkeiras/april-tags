uniform mat4 PM; // Required by our interface

attribute vec4 position;

uniform vec4 color;

void main()
{
    // Transforming The Vertex
    gl_Position = PM*position;
}
