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
        voo.addObject(vertArr, vertId);
        voo.addCode(Vx.OP_FRAG_SHADER);
        voo.addObject(fragArr, fragId);


        voo.addCode(Vx.OP_ELEMENT_ARRAY);
        voo.addCode(vxidtype);
        voo.addObject(vxid.data, vxid.id);


        for (String name :  attribMap.keySet()) {
            VxVertexAttrib vva = attribMap.get(name);
            voo.addCode(Vx.OP_VERT_ATTRIB);
            voo.addString(name);
            voo.addCode(vva.dim);
            voo.addObject(vva.fdata, vva.id);
        }

    }
}
