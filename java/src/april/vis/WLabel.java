package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class WLabel extends WComponent
{
    String value;

    public WLabel(String value)
    {
        this.value = value;
    }

    public int getHeight()
    {
        return 25;
    }

    public int getWidth()
    {
        return 100;
    }

    public void paint(Graphics2D g, int width, int height)
    {
        g.setColor(Color.gray);

        g.fillRoundRect(0, 0, width, height, 8, 8);
        g.setColor(Color.lightGray);
        g.drawRoundRect(0, 0, width, height, 8, 8);

        g.setColor(Color.black);
        g.drawString(value, 10, 20);
    }
}
