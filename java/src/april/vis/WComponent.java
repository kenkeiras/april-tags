package april.vis;

import java.awt.*;
import java.awt.event.*;

public abstract class WComponent
{
    public boolean mouseClicked(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mouseEntered(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mouseExited(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mouseMoved(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mouseDragged(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mousePressed(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mouseReleased(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract void paint(Graphics2D g, int width, int height);
}
