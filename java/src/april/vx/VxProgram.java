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

    public void appendTo(ArrayList<VxResource> resources, VxCodeOutputStream codes)
    {

        codes.writeInt(Vx.OP_VERT_SHADER);
        codes.writeLong(vertId);
        codes.writeInt(Vx.OP_FRAG_SHADER);
        codes.writeLong(fragId);

        resources.add(new VxResource(Vx.VX_BYTE_ARRAY, vertArr, vertArr.length, 1, vertId));
        resources.add(new VxResource(Vx.VX_BYTE_ARRAY, fragArr, fragArr.length, 1, fragId));


        codes.writeInt(Vx.OP_ELEMENT_ARRAY);
        codes.writeLong(vxid.id);
        codes.writeInt(vxidtype);

        resources.add(new VxResource(Vx.VX_INT_ARRAY, vxid.data, vxid.data.length, 4, vxid.id));

        codes.writeInt(Vx.OP_VERT_ATTRIB_COUNT);
        codes.writeInt(attribMap.size());

        for (String name :  attribMap.keySet()) {
            VxVertexAttrib vva = attribMap.get(name);
            codes.writeInt(Vx.OP_VERT_ATTRIB);
            codes.writeLong(vva.id);
            codes.writeInt(vva.dim);
            codes.writeStringZ(name);

            resources.add(new VxResource(Vx.VX_FLOAT_ARRAY, vva.fdata, vva.fdata.length, 4, vva.id));
        }

    }
}
