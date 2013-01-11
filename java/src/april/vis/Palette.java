package april.vis;

import java.awt.Color;
import java.util.*;

public class Palette
{
    /** Color class where each entry should have a distinct intensity,
     *  even with colorblind perception.
     */
    public static class friendly
    {
        // dark to light
        public static Color black  = new Color(   0,   0,   0);
        public static Color purple = new Color( 116,  20, 114);
        public static Color green  = new Color(   0, 111,  69);
        public static Color blue   = new Color(   0, 143, 213);
        public static Color orange = new Color( 247, 148,  30);
        public static Color olive  = new Color( 171, 214, 156);
        public static Color yellow = new Color( 255, 242,   0);
        public static Color white  = new Color( 255, 255, 255);

        public static List<Color> listAll()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(black);
            list.add(purple);
            list.add(green);
            list.add(blue);
            list.add(orange);
            list.add(olive);
            list.add(yellow);
            list.add(white);

            return list;
        }
    }

    public static class vibrant
    {
        public static Color pink    = new Color( 211,  61, 125);
        public static Color green   = new Color( 163, 221,  70);
        public static Color orange  = new Color( 255, 168,  69);
        public static Color blue    = new Color(  97, 223, 241);
        public static Color purple  = new Color( 186, 153, 251);
        public static Color tan     = new Color( 228, 224, 130);
        public static Color gray    = new Color( 238, 238, 238);

        public static List<Color> listAll()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(pink);
            list.add(green);
            list.add(orange);
            list.add(blue);
            list.add(purple);
            list.add(tan);
            list.add(gray);

            return list;
        }
    }

    public static class web
    {
        public static Color pink1   = new Color( 185,  46, 102);
        public static Color pink2   = new Color( 239,  91, 147);
        public static Color orange1 = new Color( 255, 105,  48);
        public static Color orange2 = new Color( 247, 145,   0);
        public static Color green1  = new Color( 207, 222,  33);
        public static Color green2  = new Color(  59, 161,  91);
        public static Color green3  = new Color(  44, 178, 175);
        public static Color blue1   = new Color(  56, 183, 221);
        public static Color blue2   = new Color(  70, 153, 214);
        public static Color blue3   = new Color(  32, 116, 154);
        public static Color blue4   = new Color(  48,  81, 107);
        public static Color blue5   = new Color(  58,  86, 150);
        public static Color blue6   = new Color(  16, 182, 231);

        public static List<Color> listAll()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(pink1);
            list.add(pink2);
            list.add(orange1);
            list.add(orange2);
            list.add(green1);
            list.add(green2);
            list.add(green3);
            list.add(blue1);
            list.add(blue2);
            list.add(blue3);
            list.add(blue4);
            list.add(blue5);
            list.add(blue6);

            return list;
        }
    }

    public static List<Color> listAll()
    {
        List<Color> list = new ArrayList<Color>();
        list.addAll(friendly.listAll());
        list.addAll(web.listAll());
        list.addAll(vibrant.listAll());

        return list;
    }
}
