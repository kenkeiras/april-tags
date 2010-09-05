package april.vis;

import javax.swing.*;
import java.awt.*;

// Usage:  java april.vis.VisSnapshot <path-to-snapshot>
public class VisSnapshot
{

    public static void main(String args[])
    {
        VisCanvas vc = VisSerialize.readVCFromFile(args[0]);

        JFrame jf = new JFrame("VisSnapshot: "+args[0]);
        jf.add(vc);
        jf.setSize(640,480);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }

}