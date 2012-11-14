package april.vx;

import java.util.*;

public interface VxObject
{
    public void appendTo(HashSet<VxResource> resources, VxCodeOutputStream vxout);
}