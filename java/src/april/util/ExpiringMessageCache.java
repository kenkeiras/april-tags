package april.util;

public class ExpiringMessageCache<T>
{
    T    t;
    long receivedUtime;  //
    double maxAge;

    // prevent stale message from becoming current message
    boolean ensureMonotonicUtimes;
    long msgUtime;  // used for monotonicity

    public ExpiringMessageCache(double maxAge)
    {
        this.maxAge = maxAge;
    }

    public ExpiringMessageCache(double maxAge, boolean increasingUtimes)
    {
        ensureMonotonicUtimes = increasingUtimes;
        this.maxAge = maxAge;
    }

    public void put(T t, long utime)
    {
        put(t, utime, utime);
    }

    /**
     * Put new message in cache that can ensure monotonic msgUtimes
     * even when the message comes form a different host.
     * @param T the data to cache
     * @param msgUtime utime in message (host time of sender)
     * @param receivedUtime generally, local TimeUtil.utime(), but not always
     **/
    public synchronized void put(T t, long msgUtime, long receivedUtime)
    {
        if (!ensureMonotonicUtimes || msgUtime >= this.msgUtime) {
            this.t = t;
            this.msgUtime = msgUtime;
            this.receivedUtime = receivedUtime;
        }
    }

    public synchronized T get()
    {
        if (t == null)
            return null;

        long now = TimeUtil.utime();
        double age = (now - receivedUtime) / 1000000.0;
        if (age > maxAge)
            return null;

        return t;
    }
}