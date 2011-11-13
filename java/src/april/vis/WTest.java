package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class WTest
{
    public static void main(String args[])
    {
        WDragPanel dragPanel = new WDragPanel();
        dragPanel.add(new WLabel("abc"));
        dragPanel.add(new WLabel("def"));
        dragPanel.add(new WLabel("ghi"));
        dragPanel.add(new WLabel("jkl"));
        dragPanel.add(new WLabel("mno"));
        dragPanel.add(new WLabel("pqr"));
        dragPanel.add(new WLabel("stu"));
        dragPanel.add(new WLabel("vwx"));

        if (true) {
            WDragPanel dragPanel2 = new WDragPanel();
            dragPanel2.add(new WInset(new WLabel("1"), 0, 0, 0, 40));
            dragPanel2.add(new WInset(new WLabel("2"), 0, 0, 0, 40));
            dragPanel2.add(new WInset(new WLabel("3"), 0, 0, 0, 40));
            dragPanel.add(dragPanel2);
        }

        if (true) {
            WDragPanel dragPanel2 = new WDragPanel();
            dragPanel2.add(new WLabel("i"));
            dragPanel2.add(new WLabel("ii"));
            dragPanel2.add(new WLabel("iii"));
            dragPanel.add(dragPanel2);
        }

        JFrame jf = new JFrame("WTest");
        jf.setLayout(new BorderLayout());
        jf.add(new WAdapter(dragPanel), BorderLayout.CENTER);
        jf.setSize(400,600);
        jf.setVisible(true);
    }
}
