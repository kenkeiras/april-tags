package april.vis;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;

import april.jmat.geom.*;

import java.util.*;

/** Workhorse of 2D and 3D data viewing. **/
public class VisData implements VisObject
{
    ArrayList<double[]> points = new ArrayList<double[]>();
    ArrayList<VisDataStyle> styles = new ArrayList<VisDataStyle>();

    public VisData(Object ... args)
    {
        add(args);
    }

    public synchronized void clear()
    {
        points.clear();
    }

    public synchronized void add(Object ... args)
    {
        for (int i = 0; i < args.length; i++) {

            Object o = args[i];

            if (o instanceof VisDataStyle)
                styles.add((VisDataStyle) o);

            if(o instanceof double[][]) {
                double [][] l = (double[][])o;
                for(int j = 0; j < l.length; j++){
                    points.add(l[j]);
                }
            }

            if (o instanceof double[]) {
                points.add((double[]) o);
            }

            if (o instanceof ArrayList) {
                ArrayList al = (ArrayList) o;

                points.ensureCapacity(points.size() + al.size());

                for (Object p : al) {
                    assert (p instanceof double[]);
                    points.add((double[]) p);
                }
            }
        }
    }

    public synchronized void render(VisContext vc, GL gl, GLU glu)
    {
        for (VisDataStyle style : styles) {
            style.renderStyle(vc, gl, glu, points);
        }
    }
}
