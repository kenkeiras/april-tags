package april.vx;

import java.util.*;

public class VxProgram implements VxObject
{

    final long vertId, fragId;
    final byte vertArr[], fragArr[];

    VxIndexData vxid;
    int vxidtype; // VX_POINTS, VX_TRIANGLES, etc

    final HashMap<String,VxVertexAttrib> attribMap = new HashMap();

    public VxProgram(byte vertArr[], long vertId,
                     byte fragArr[], long fragId)
    {
        this.vertId = vertId;
        this.fragId = fragId;

        this.vertArr = vertArr;
        this.fragArr = fragArr;
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

    public void appendTo(VxObjOpcodes voo)
    {

        voo.addCode(Vx.OP_VERT_SHADER);
        voo.addResource(null, Vx.VX_BYTE_ARRAY, vertArr, vertArr.length, 1, vertId);
        voo.addCode(Vx.OP_FRAG_SHADER);
        voo.addResource(null, Vx.VX_BYTE_ARRAY, fragArr, fragArr.length, 1, fragId);


        voo.addCode(Vx.OP_ELEMENT_ARRAY);
        voo.addCode(vxidtype);
        voo.addResource(null, Vx.VX_INT_ARRAY, vxid.data, vxid.data.length, 4, vxid.id);


        voo.addCode(attribMap.size());
        for (String name :  attribMap.keySet()) {
            VxVertexAttrib vva = attribMap.get(name);
            voo.addCode(Vx.OP_VERT_ATTRIB);
            voo.addCode(vva.dim);
            voo.addResource(name, Vx.VX_FLOAT_ARRAY, vva.fdata, vva.fdata.length, 4, vva.id);
        }

    }
}
