package april.vx;


public class VxTest
{

    public static void main(String args[])
    {

        VxProgram vp = new VxProgram("foo".getBytes(), VxUtil.allocateID(),
                                     "bar".getBytes(), VxUtil.allocateID());

        float pts[] = { 1.0f, 1.0f,
                        0.0f, 1.0f,
                        0.0f, 0.0f,
                        1.0f, 0.0f};

        VxVertexAttrib points = new VxVertexAttrib(pts, 2);
        vp.setVertexAttrib("position", points);


        int idxs[] = {0,1,2,
                      2,3,0};
        VxIndexData index = new VxIndexData(idxs);
        vp.setElementArray(index, Vx.GL_TRIANGLES);


        VxLocalServer vxls = new VxLocalServer(420,420);
        VxWorld vw = new VxWorld(vxls);

        vw.getBuffer("first-buffer").stage(vp);
        vw.getBuffer("first-buffer").commit();


        vxls.render();
    }
}