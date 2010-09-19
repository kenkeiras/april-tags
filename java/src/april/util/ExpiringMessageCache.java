package april.util;

public class ExpiringMessageCache<T>
{
    T    t;
    long utime;
    double maxAge;

    public ExpiringMessageCache(double maxAge)
    {
        this.maxAge = maxAge;
    }

    public synchronized void put(T t, long utime)
    {
        this.t = t;
        this.utime = utime;
    }

    public synchronized T get()
    {
        if (t == null)
            return null;

        long now = TimeUtil.utime();
        double age = (now - utime) / 1000000.0;
        if (age > maxAge)
            return null;

        return t;
    }
}