package april.vx;

import java.util.*;

public class VxProgram implements VxObject
{

    final VxResource vertResc, fragResc;

    final HashMap<String,VxVertexAttrib> attribMap = new HashMap();
    final HashMap<String, float[][]> uniformMatrixfvMap = new HashMap();

    VxIndexData vxid;
    int vxidtype; // VX_POINTS, VX_TRIANGLES, etc

    public VxProgram(byte vertArr[], long vertId,
                     byte fragArr[], long fragId)
    {
        this(new VxResource(Vx.GL_BYTE, vertArr, vertArr.length, 1, vertId),
             new VxResource(Vx.GL_BYTE, fragArr, fragArr.length, 1, fragId));
    }

    public VxProgram(VxResource vertexShader, VxResource fragmentShader)
    {
        this.vertResc = vertexShader;
        this.fragResc = fragmentShader;
    }

    public void setElementArray(VxIndexData vxid, int type)
    {
        this.vxid = vxid;
        this.vxidtype = type;
    }

    public void setVertexAttrib(String name, VxVertexAttrib vva)
    {
        attribMap.put(name, vva);
    }

    public void setUniform(String name, float  vec[][])
    {
        uniformMatrixfvMap.put(name, vec);
    }

    public void appendTo(HashSet<VxResource> resources, VxCodeOutputStream codes)
    {

        codes.writeInt(Vx.OP_PROGRAM);
        codes.writeLong(vertResc.id);
        codes.writeLong(fragResc.id);

        resources.add(vertResc);
        resources.add(fragResc);

        codes.writeInt(Vx.OP_VERT_ATTRIB_COUNT);
        codes.writeInt(attribMap.size());

        for (String name :  attribMap.keySet()) {
            VxVertexAttrib vva = attribMap.get(name);
            codes.writeInt(Vx.OP_VERT_ATTRIB);
            codes.writeLong(vva.id);
            codes.writeInt(vva.dim);
            codes.writeStringZ(name);

            resources.add(new VxResource(Vx.GL_FLOAT, vva.fdata, vva.fdata.length, 4, vva.id));
        }

        // Float matrix uniforms
        codes.writeInt(Vx.OP_UNIFORM_COUNT); // Sum all uniforms here
        codes.writeInt(uniformMatrixfvMap.size());

        for (String name :  uniformMatrixfvMap.keySet()) {
            float fv[][] = uniformMatrixfvMap.get(name);
            codes.writeInt(Vx.OP_UNIFORM_MATRIX_FV);
            codes.writeStringZ(name);
            int dim = fv.length;
            codes.writeInt(dim*dim); // Only square matrices are supported in ES 2.0
            codes.writeInt(1); //count, right now only sending one matrix
            codes.writeInt(1); // Transpose = true to convert to column-major

            for (int i = 0; i < dim; i++)
                for (int j = 0; j < dim; j++)
                    codes.writeFloat(fv[i][j]);
        }

        codes.writeInt(Vx.OP_ELEMENT_ARRAY);
        codes.writeLong(vxid.id);
        codes.writeInt(vxidtype);

        resources.add(new VxResource(Vx.GL_UNSIGNED_INT, vxid.data, vxid.data.length, 4, vxid.id));
    }
}
