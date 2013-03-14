package april.util;

import april.jmat.*;


public class GPSUtil
{
    // check whether a message has a valid checksum
    public static boolean checksum(String nmea)
    {
        int len = nmea.length();
        if (len < 3)
            return false;

        try {
            String checksumString = nmea.substring(len - 2, len);
            int target = Integer.valueOf(checksumString, 16);
            int chk = computeChecksum(nmea);
            if (chk == target)
                return true;
        } catch (NumberFormatException e) {
            // System.out.println(e); e.printStackTrace();
        }

        return false;
    }

    // Computes checksum on string of form "$xxxxxxxx*" by XORing
    // all characters EXCLUSIVE of the $ and the *
    public static int computeChecksum(String nmea)
    {
        char chk = 0;
        for (int i = 1; i < nmea.length(); i++){
            char c = nmea.charAt(i);
            if (c == 0 || c == '*')
                break;
            chk ^= c;
        }
        return chk;
    }

    // Decimal, minutes, seconds to decimal degrees
    public static double degMinSec2DecDeg(double deg, double min,
                                          double sec, String ori)
    {
        double magnitude = deg + min/60 + sec/3600;

        double sign = getSign(ori.charAt(0));
        return sign*magnitude;

    }

    //Converts a decimal degree minutes of the form ddmm.mmmm... into a
    //decimal degrees of the form dd.dddddd...
    public static double decDegMin2DecDeg(double ddmm)
    {
        double sign = MathUtil.sign(ddmm);
        ddmm *= sign;

        double deg_part = Math.floor(ddmm / 100.0);
        double min_part = ddmm % 100.0;

        double final_answer = (deg_part + min_part/60.0)*sign;
        return final_answer;
    }


    // Convention: East and North are positive
    public static double getSign(char a)
    {
        char c = Character.toUpperCase(a);
        if (c=='W' || c=='S')
            return -1;
        return 1;
    }
}
