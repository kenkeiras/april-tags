package april.vx;
import java.util.concurrent.atomic.*;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.*;
import april.jmat.*;

public class VxUtil
{
    static AtomicLong nextId = new AtomicLong(1);

    public static long allocateID()
    {
        return nextId.getAndIncrement();
    }

    public static double[][] gluPerspective(double fovy_degrees, double aspect, double znear, double zfar)
    {
        double M[][] = new double[4][4];

        double f = 1.0 / Math.tan(Math.toRadians(fovy_degrees)/2);

        M[0][0] = f/aspect;
        M[1][1] = f;
        M[2][2] = (zfar+znear)/(znear-zfar);
        M[2][3] = 2*zfar*znear / (znear-zfar);
        M[3][2] = -1;

        return M;
    }

    public static double[][] lookAt(double eye[], double c[], double up[])
    {
        up = LinAlg.normalize(up);
        double f[] = LinAlg.normalize(LinAlg.subtract(c, eye));

        double s[] = LinAlg.crossProduct(f, up);
        double u[] = LinAlg.crossProduct(s, f);

        double M[][] = new double[4][4];
        M[0][0] = s[0];
        M[0][1] = s[1];
        M[0][2] = s[2];
        M[1][0] = u[0];
        M[1][1] = u[1];
        M[1][2] = u[2];
        M[2][0] = -f[0];
        M[2][1] = -f[1];
        M[2][2] = -f[2];
        M[3][3] = 1;

        double T[][] = new double[4][4];
        T[0][3] = -eye[0];
        T[1][3] = -eye[1];
        T[2][3] = -eye[2];
        T[0][0] = 1;
        T[1][1] = 1;
        T[2][2] = 1;
        T[3][3] = 1;
        return LinAlg.matrixAB(M, T);
    }

    public static float[][] copyFloats(double mat[][])
    {
        float out[][] = new float[mat.length][];

        for (int row = 0; row < mat.length; row++)
            out[row] = LinAlg.copyFloats(mat[row]);

        return out;
    }

    public static byte[] copyStringZ(String str)
    {
        byte buf_short[] = str.getBytes();
        byte buf_full[] = new byte[buf_short.length +1];
        System.arraycopy(buf_short,0,buf_full,0, buf_short.length);
        //last index is implicitly '\0'

        return buf_full;
    }

    public static byte[] readFileStringZ(String filename) throws IOException
    {
        File file = new File(filename);
        FileInputStream fis = new FileInputStream(file);

        int len = (int)file.length();

        byte fbytes[] = new byte[len+1];
        int rd = fis.read(fbytes);
        assert(rd == len);
        //last index of fbytes is implicitly '\0'

        return fbytes;
    }

    public static BufferedImage convertAndCopyImage(BufferedImage in, int type)
    {

        int w = in.getWidth();
        int h = in.getHeight();

        BufferedImage out=new BufferedImage(w,h,type);
        Graphics g = out.getGraphics();

        g.drawImage(in, 0, 0, null);

        g.dispose();

        return out;
    }

    // Converts primitive arrays to a byte array, big endian
    public static byte[] copyByteArray(Object obj)
    {
        VxCodeOutputStream vout = new VxCodeOutputStream();
        if(double[].class == obj.getClass()) {
            double[] array = (double[]) obj;
            for (double v : array)
                vout.writeDouble(v);
        } else if(float[].class == obj.getClass()) {
            float[] array = (float[]) obj;
            for (float v : array)
                vout.writeFloat(v);
        } else if(int[].class == obj.getClass()) {
            int[] array = (int[]) obj;
            for (int v : array)
                vout.writeInt(v);
        } else if(byte[].class == obj.getClass()) {
            return (byte[]) obj;
        } else {
            assert(false);
        }

        return vout.toByteArray();
    }

    // The following methods take big endian byte buffers,
    // and parses them to primitive arrays of the appropriate type
    public static Object copyToType(byte[] buf, int type)
    {
        switch(type) {
            case Vx.GL_FLOAT:
                return copyFloatArray(buf);
            case Vx.GL_INT:
            case Vx.GL_UNSIGNED_INT:
                return copyIntArray(buf);
            case Vx.GL_BYTE:
            case Vx.GL_UNSIGNED_BYTE:
                return buf;
        }

        System.out.println("ERR: unsupported array type "+type);
        System.exit(1);
        return null;
    }

    public static float[] copyFloatArray(byte array[])
    {
        FloatBuffer fb =  ByteBuffer.wrap(array).asFloatBuffer();
        float out[] = new float[fb.remaining()];
        fb.get(out);
        return out;
    }

    public static int[] copyIntArray(byte array[])
    {
        IntBuffer ib =  ByteBuffer.wrap(array).asIntBuffer();
        int out[] = new int[ib.remaining()];
        ib.get(out);
        return out;
    }

}
