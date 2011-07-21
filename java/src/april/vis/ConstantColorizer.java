package april.vis;

public class ConstantColorizer implements Colorizer
{
    int aarrggbb;

    public ConstantColorizer(int aarrggbb)
    {
        this.aarrggbb = aarrggbb;
    }

    public int colorize(double p[])
    {
        return aarrggbb;
    }
}
