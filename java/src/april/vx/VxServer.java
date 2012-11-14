package april.vx;

import java.util.*;

public interface VxServer
{
    public void update(String name, HashSet<VxResource> resources, VxCodeOutputStream codes);

}