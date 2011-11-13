package april.vis;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class WDragPanel extends WComponent
{
    ArrayList<WComponent> items = new ArrayList<WComponent>();
    int ys[];

    WComponent selectedItem;

    // where is the actual mouse pointer?
    int selectedMouseX = -1, selectedMouseY = -1;

    // where, with respect to the item's origin, is the mouse pointer?
    int selectedOffsetX = -1, selectedOffsetY = -1;

    public WDragPanel()
    {
    }

    public void add(WComponent item)
    {
        items.add(item);
        ys = null;
        recompute();
    }

    boolean dispatchMouseEvent(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        for (int i = 0; i < items.size(); i++) {
            WComponent item = items.get(i);

            if (my >= ys[i] && my <= ys[i] + item.getHeight()) {

                boolean handled = false;

                int tx = mx;
                int ty = my - ys[i];

                if (item == selectedItem) {
                    tx = mx - (selectedMouseX - selectedOffsetX);
                    ty = my - (selectedMouseY - selectedOffsetY);
                }

                switch (e.getID()) {
                    case MouseEvent.MOUSE_PRESSED:
                        handled = item.mousePressed(wadapter, e, tx, ty);
                        break;
                    case MouseEvent.MOUSE_RELEASED:
                        handled = item.mouseReleased(wadapter, e, tx, ty);
                        break;
                    case MouseEvent.MOUSE_CLICKED:
                        handled = item.mouseClicked(wadapter, e, tx, ty);
                        break;
                    case MouseEvent.MOUSE_DRAGGED:
                        handled = item.mouseDragged(wadapter, e, tx, ty);
                        break;
                    case MouseEvent.MOUSE_MOVED:
                        handled = item.mouseMoved(wadapter, e, tx, ty);
                        break;
                    case MouseEvent.MOUSE_WHEEL:
//                        handled = item.mouseWheel(wadapter, e, tx, ty);
                        break;
                    case MouseEvent.MOUSE_ENTERED:
                        handled = false;
                        break;
                    case MouseEvent.MOUSE_EXITED:
                        handled = false;
                        break;
                    default:
                        System.out.println("Unhandled mouse event id: "+e.getID());
                        handled = false;
                        break;
                }

                if (handled)
                    return true;
            }
        }
        return false;
    }

    public boolean mouseClicked(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        return false;
    }

    public boolean mouseEntered(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        return false;
    }

    public boolean mouseExited(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        return false;
    }

    public boolean mouseMoved(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        return false;
    }

    public boolean mouseDragged(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        if (selectedItem != null) {
            selectedMouseX = mx;
            selectedMouseY = my;
            recompute();
            wadapter.repaint();
            return true;
        }

        return false;
    }

    public boolean mousePressed(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        // Find the Item that is being pressed (if any)
        int y0 = 0;
        for (int i = 0; i < items.size(); i++) {
            WComponent item = items.get(i);
            int y1 = y0 + item.getHeight();

            if (my >= y0 && my <= y1) {
                selectedItem = item;
                selectedMouseX = mx;
                selectedMouseY = my;
                selectedOffsetX = mx;
                selectedOffsetY = my - y0;
            }

            y0 = y1;
            wadapter.repaint();
        }

        return selectedItem != null;
    }

    public boolean mouseReleased(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        selectedItem = null;
        wadapter.repaint();

        return false;
    }

    public int getWidth()
    {
        int x = 0;
        for (int i = 0; i < items.size(); i++) {
            x = Math.max(x, items.get(i).getWidth());
        }
        return x;
    }

    public int getHeight()
    {
        int y = 0;
        for (int i = 0; i < items.size(); i++) {
            y += items.get(i).getHeight();
        }
        return y;
    }

    static class Pos implements Comparable<Pos>
    {
        WComponent item;
        int  ymid;

        public int compareTo(Pos p)
        {
            return ymid - p.ymid;
        }
    }

    // Compute the y coordinate for each item, reordering the items if necessary.
    void recompute()
    {
        ArrayList<Pos> poses = new ArrayList<Pos>();

        // what is the y coordinate of the origin of the selected item?
        int sy = selectedMouseY - selectedOffsetY;

        int y0 = 0;

        for (int i = 0; i < items.size(); i++) {
            WComponent item = items.get(i);

            int ymid = y0+item.getHeight() / 2;

            Pos p = new Pos();
            p.item = item;
            p.ymid = ymid;

            if (item == selectedItem)
                p.ymid = sy + selectedItem.getHeight() / 2;

            y0 += item.getHeight();

            poses.add(p);
        }

        Collections.sort(poses);

        int ys[] = new int[items.size()];
        y0 = 0;

        for (int i = 0; i < poses.size(); i++) {
            Pos p = poses.get(i);
            items.set(i, p.item);
            ys[i] = y0;
            y0 += p.item.getHeight();
        }

        this.ys = ys;
    }

    public void paint(Graphics2D g, int width, int height)
    {
        g.setColor(new Color(200,200,200));
        for (int i = 0; i < items.size(); i++) {
            WComponent item = items.get(i);

            // selected item gets drawn "on top", so skip for now.
            if (item == selectedItem)
                continue;

            g.translate(0, ys[i]);
            item.paint(g, width, item.getHeight());
            g.translate(0, -ys[i]);
        }

        if (selectedItem != null) {
            // draw last/on top.
            g.translate(selectedMouseX - selectedOffsetX, selectedMouseY - selectedOffsetY);
            selectedItem.paint(g, width, selectedItem.getHeight());
            g.translate(-(selectedMouseX - selectedOffsetX), -(selectedMouseY - selectedOffsetY));
        }
    }
}
