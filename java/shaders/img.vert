uniform mat4 proj;

attribute vec4 position;

attribute vec2 texIn;
varying vec2 texOut;


void main()
{
    // Transforming The Vertex
    gl_Position = proj*position;//gl_Vertex;//gl_ModelViewProjectionMatrix * gl_Vertex;

    texOut = texIn; // will get interpolated
}
