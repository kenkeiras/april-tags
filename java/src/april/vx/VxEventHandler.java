package april.vx;

import java.awt.event.*;
import april.jmat.geom.*;

public interface VxEventHandler
{
    /** Handlers with lower dispatch order are called first **/
    public int getDispatchOrder();

    /** Return true if you've consumed the event. **/
    public boolean mousePressed(VxCanvas vc, VxLayer vl, VxCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e);
    public boolean mouseReleased(VxCanvas vc, VxLayer vl, VxCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e);
    public boolean mouseClicked(VxCanvas vc, VxLayer vl, VxCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e);
    public boolean mouseDragged(VxCanvas vc, VxLayer vl, VxCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e);
    public boolean mouseMoved(VxCanvas vc, VxLayer vl, VxCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e);
    public boolean mouseWheel(VxCanvas vc, VxLayer vl, VxCanvas.RenderInfo rinfo, GRay3D ray, MouseWheelEvent e);

    public boolean keyPressed(VxCanvas vc, VxLayer vl, VxCanvas.RenderInfo rinfo, KeyEvent e);
    public boolean keyTyped(VxCanvas vc, VxLayer vl, VxCanvas.RenderInfo rinfo, KeyEvent e);
    public boolean keyReleased(VxCanvas vc, VxLayer vl, VxCanvas.RenderInfo rinfo, KeyEvent e);
}
