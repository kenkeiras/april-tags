package april.util;

public class EnvUtil
{
    public static final boolean getProperty(String name, boolean def)
    {
        String s = System.getProperty(name);
        if (s==null)
            return def;

        s = s.trim().toLowerCase();
        if (s.equals("0") || s.equals("false") || s.equals("no"))
            return false;
        if (s.equals("1") || s.equals("true") || s.equals("yes"))
            return true;
        System.out.println(name+": Bad value "+s);
        return def;
    }

    public static final int getProperty(String name, int def)
    {
        String s = System.getProperty(name);
        if (s==null)
            return def;

        try {
            return Integer.parseInt(s);
        } catch (Exception ex) {
        }

        System.out.println(name+": Bad value "+s);
        return def;
    }

}
