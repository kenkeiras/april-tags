package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class WTest
{
    public static void main(String args[])
    {
        Color layerBorder = new Color(220, 220, 220);
        Color layerBackground = new Color(255, 255, 255);

        Color bufferBorder = new Color(200, 200, 200);
        Color bufferBackground = new Color(220, 220, 220);

        WDragPanel layerPanel = new WDragPanel();
        layerPanel.backgroundColor = layerBackground;
        layerPanel.grabColor = layerBorder;

        for (int i = 0; i < 3; i++) {
            layerPanel.add(new WInset(new WLabel(""+i, Color.black, layerPanel.backgroundColor),
                                       1, 1, 1, 1,
                                       layerPanel.grabColor));
        }

        for (int layernum = 0; layernum < 3; layernum ++) {
            WDragPanel bufferPanel = new WDragPanel();

            bufferPanel.backgroundColor = bufferBackground;
            bufferPanel.grabColor = bufferBorder;

            bufferPanel.add(new WInset(new WLabel("1", Color.black, bufferPanel.backgroundColor),
                                      1, 1, 1, 1,
                                      bufferPanel.grabColor));

            bufferPanel.add(new WInset(new WLabel("2", Color.black, bufferPanel.backgroundColor),
                                      1, 1, 1, 1,
                                      bufferPanel.grabColor));

            bufferPanel.add(new WInset(new WLabel("3", Color.black, bufferPanel.backgroundColor),
                                      1, 1, 1, 1,
                                      bufferPanel.grabColor));

            bufferPanel.add(new WInset(new WLabel("4", Color.black, bufferPanel.backgroundColor),
                                      1, 1, 1, 1,
                                      bufferPanel.grabColor));


            WVerticalPanel vp = new WVerticalPanel();
            vp.backgroundColor = layerBackground;
            vp.add(new WLabel("Test "+layernum));
            vp.add(new WInset(bufferPanel, 4, 4, 4, 40));
            layerPanel.add(new WInset(vp, 2, 1, 2, 1, bufferBorder));
        }

        if (true) {
            WDragPanel bufferPanel = new WDragPanel();
            bufferPanel.add(new WLabel("i"));
            bufferPanel.add(new WLabel("ii"));
            bufferPanel.add(new WLabel("iii"));
            WHorizontalPanel whp = new WHorizontalPanel();
            whp.add(new WCheckbox(true));
            whp.add(new WLabel("Checkbox"));
            bufferPanel.add(whp);
            layerPanel.add(bufferPanel);
        }

        JFrame jf = new JFrame("WTest");
        jf.setLayout(new BorderLayout());
        jf.add(new JScrollPane(new WAdapter(layerPanel)), BorderLayout.CENTER);
        jf.setSize(400,600);
        jf.setVisible(true);
    }
}
