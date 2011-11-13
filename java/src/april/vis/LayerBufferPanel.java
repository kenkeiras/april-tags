package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class LayerBufferPanel extends JPanel
{
/*
    DragPanel layerDrags = new DragPanel();

    public LayerBufferPanel(VisCanvas vc)
    {
        synchronized(vc.layers) {
            for (VisLayer layer : vc.layers) {
                System.out.println("add layer");
                LayerItem layerItem = new LayerItem(layer);
                layerDrags.addItem(layerItem);
            }
        }

        setLayout(new BorderLayout());
//        add(new JScrollPane(layerDrags), BorderLayout.CENTER);
    }

    static class LayerItem implements DragPanel.Item
    {
        VisLayer layer;
        DragPanel bufferDrags = new DragPanel();

        LayerItem(VisLayer layer)
        {
            this.layer = layer;

            synchronized(layer.world.buffers) {
                for (VisWorld.Buffer buffer : layer.world.buffers) {
                    System.out.println("  add buffer");
                    BufferItem bufferItem = new BufferItem(buffer);
                    bufferDrags.addItem(bufferItem);
                }
            }
        }

        public int getHeight()
        {
            return 30 + (int) bufferDrags.getPreferredSize().getHeight();
        }

        public int getWidth()
        {
            return (int) bufferDrags.getPreferredSize().getWidth();
        }

        public void paint(DragPanel dp, Graphics2D g, boolean selected)
        {
            System.out.println("paint layer");

            if (selected)
                g.setColor(Color.blue);
            else
                g.setColor(Color.gray);
//            g.fillRoundRect(0, 0, dp.getWidth(), getHeight(), 8, 8);
            g.setColor(Color.lightGray);
            g.drawRoundRect(0, 0, dp.getWidth(), getHeight(), 8, 8);

            g.setColor(Color.black);
            g.drawString("Layer", 0, 20);

            g.translate(0, 30);
            bufferDrags.paint(g);
            g.translate(0, -30);
        }
    }

    static class BufferItem implements DragPanel.Item
    {
        VisWorld.Buffer buffer;

        BufferItem(VisWorld.Buffer buffer)
        {
            this.buffer = buffer;
        }

        public int getHeight()
        {
            return 20;
        }

        public int getWidth()
        {
            return 50;
        }

        public void paint(DragPanel dp, Graphics2D g, boolean selected)
        {
            System.out.printf("  paint buffer %d %d\n", dp.getWidth(), getHeight());

            if (selected)
                g.setColor(Color.cyan);
            else
                g.setColor(Color.green);
            g.fillRoundRect(20, 0, dp.getWidth(), getHeight(), 8, 8);
            g.setColor(Color.black);
            g.drawRoundRect(20, 0, dp.getWidth(), getHeight(), 8, 8);
        }
    }
*/
}
