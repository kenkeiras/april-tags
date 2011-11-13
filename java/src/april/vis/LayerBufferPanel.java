package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class LayerBufferPanel extends JPanel
{
    DragPanel layerDrags = new DragPanel();

    public LayerBufferPanel(VisCanvas vc)
    {
        setLayout(new BorderLayout());
        add(new JScrollPane(layerDrags), BorderLayout.CENTER);

        synchronized(vc.layers) {
            for (VisLayer layer : vc.layers) {

            }
        }
    }

    static class LayerItem implements DragPanel.Item
    {
        VisLayer layer;

        LayerItem(VisLayer layer)
        {
            this.layer = layer;

            synchronized(layer.world.buffers) {
                for (VisWorld.Buffer buffer : layer.world.buffers) {
                    BufferItem bufferItem = new BufferItem(buffer);
                }
            }
        }

        public int getHeight()
        {
            return 50;
        }

        public int getWidth()
        {
            return 50;
        }

        public void paint(DragPanel dp, Graphics2D g, boolean selected)
        {
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
            return 50;
        }

        public int getWidth()
        {
            return 50;
        }

        public void paint(DragPanel dp, Graphics2D g, boolean selected)
        {
        }
    }
}
