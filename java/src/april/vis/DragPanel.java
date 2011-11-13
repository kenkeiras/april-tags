package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class DragPanel extends JComponent implements MouseListener, MouseMotionListener
{
    ArrayList<Item> items = new ArrayList<Item>();

    Item selectedItem = null;

    // where is the actual mouse pointer?
    int selectedMouseX = -1, selectedMouseY = -1;

    // where, with respect to the item's origin, is the mouse pointer?
    int selectedOffsetX = -1, selectedOffsetY = -1;

    public interface Item
    {
        // how many vertical pixels will the item draw? This should be
        // a constant value for the item.
        public int getHeight();

        // Draw the item at y=0, x=0, respecting the getHeight. Do not clear the background.
        public void paint(DragPanel dp, Graphics2D g, boolean selected);
    }

    public DragPanel()
    {
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public void add(Item item)
    {
        items.add(item);
    }

    public void paint(Graphics _g)
    {
        Graphics2D g = (Graphics2D) _g;
        g.setColor(Color.white);
        g.fillRect(0, 0, getWidth(), getHeight());

        int y = 0;

        g.setColor(new Color(200,200,200));
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);

            if (item == selectedItem)
                continue;

            g.translate(0, y);
            item.paint(this, g, false);
            g.translate(0, -y);

            y += item.getHeight();
        }

        if (selectedItem != null) {
            g.translate(selectedMouseX - selectedOffsetX, selectedMouseY - selectedOffsetY);
            selectedItem.paint(this, g, true);
            g.translate(-(selectedMouseX - selectedOffsetX), -(selectedMouseY - selectedOffsetY));
        }
    }

    public void mouseClicked(MouseEvent e)
    {
    }

    public void mouseEntered(MouseEvent e)
    {
    }

    public void mouseExited(MouseEvent e)
    {
    }

    public void mouseMoved(MouseEvent e)
    {
    }

    public void mouseDragged(MouseEvent e)
    {
        if (selectedItem != null) {
            selectedMouseX = e.getX();
            selectedMouseY = e.getY();
        }

        repaint();
    }

    public void mousePressed(MouseEvent e)
    {
        // Find the Item that is being pressed (if any)
        int y0 = 0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int y1 = y0 + item.getHeight();

            if (e.getY() >= y0 && e.getY() <= y1) {
                selectedItem = item;
                selectedMouseX = e.getX();
                selectedMouseY = e.getY();
                selectedOffsetX = e.getX();
                selectedOffsetY = e.getY() - y0;
            }

            y0 = y1;
        }
        repaint();
    }

    public void mouseReleased(MouseEvent e)
    {
        selectedItem = null;
        repaint();
    }

    static class TextItem implements DragPanel.Item
    {
        String name;

        public TextItem(String name)
        {
            this.name = name;
        }

        public int getHeight()
        {
            return 25;
        }

        public void paint(DragPanel dp, Graphics2D g, boolean selected)
        {
            if (selected)
                g.setColor(Color.blue);
            else
                g.setColor(Color.gray);
            g.fillRoundRect(0, 0, dp.getWidth(), getHeight(), 3, 3);
            g.setColor(Color.lightGray);
            g.drawRoundRect(0, 0, dp.getWidth(), getHeight(), 3, 3);

            g.setColor(Color.black);
            g.drawString(name, 10, 20);
        }
    }

    public static void main(String args[])
    {
        JFrame jf = new JFrame("DragPanel Test");
        jf.setLayout(new BorderLayout());

        DragPanel dp = new DragPanel();
        dp.add(new TextItem("abc"));
        dp.add(new TextItem("def"));
        dp.add(new TextItem("ghi"));
        dp.add(new TextItem("jkl"));
        jf.add(dp, BorderLayout.CENTER);

        jf.setSize(600,400);
        jf.setVisible(true);
    }
}
