package april.util;

import java.awt.image.*;
import java.util.*;

import april.image.*;

/** pixel coordinates are obtained via:
    ix = (x - x0) / metersPerPixel
    iy = (y - y0) / metersPerPixel

    access internal state with care!
**/
public final class GridMap
{
    public double x0, y0;        // minimum x, y (lower-left corner of lower-left pixel);
    public double metersPerPixel;

    public int    width, height; // in pixels. Always a multiple of four.
    public byte   data[];

    public byte defaultFill;

    /**
     * Create a new grid map. The size will be adjusted slightly so
     * that the gridmap contains an integer number of pixels of
     * exactly metersPerPixel dimensions. When allocating new pixels,
     * the value in defaultFill will be used. Note that the resulting
     * GridMap will generally be somewhat larger than the requested
     * size in order to accommodate padding considerations.
     *
     * @param x0                Minimum x coordinate. (Lower-left corner of lower-left pixel)
     * @param y0                Minimum y coordinate. (Lower-left corner of lower-left pixel)
     * @param sizex             Requested map size in meters (x) (might be enlarged slightly)
     * @param sizey             Requested map size in meters (y) (might be enlarged slightly)
     * @param metersPerPixel    Exact dimension of each pixel in map
     * @param defaultFill       Default fill value for grid
     * @return                  GridMap class
     **/
    public static GridMap makeMeters(double x0, double y0, double sizex, double sizey, double metersPerPixel, int defaultFill)
    {
        // compute pixel dimensions.
        int width = (int) (sizex / metersPerPixel + 1);
        int height = (int) (sizey / metersPerPixel + 1);

        return makePixels(x0, y0, width, height, metersPerPixel, defaultFill, true);
    }

    /**
     * Create a new grid map. The size will be adjusted slightly so
     * that the gridmap contains an integer number of pixels of
     * exactly metersPerPixel dimensions. When allocating new pixels,
     * the value in defaultFill will be used. Note that the resulting
     * GridMap will generally be somewhat larger than the requested
     * size in order to accommodate padding considerations.
     *
     * @param x0                Minimum x coordinate. (Lower-left corner of lower-left pixel)
     * @param y0                Minimum y coordinate. (Lower-left corner of lower-left pixel)
     * @param width             Dimensions of gridmap in pixels.
     * @param height            Dimensions of gridmap in pixels
     * @param metersPerPixel    Exact dimension of each pixel in map
     * @param defaultFill       Default fill value for grid
     * @param roundUpDimensions If true, width and height will be rounded up to multiples of 4 (strongly recommended).
     * @return                  GridMap class
     **/
    public static GridMap makePixels(double x0, double y0, int width, int height, double metersPerPixel, int defaultFill, boolean roundUpDimensions)
    {
        GridMap gm = new GridMap();

        gm.x0 = x0;
        gm.y0 = y0;
        gm.metersPerPixel = metersPerPixel;
        gm.defaultFill = (byte) defaultFill;

        // compute pixel dimensions
        gm.width = width;
        gm.height = height;

        if (roundUpDimensions) {
            // round up to multiple of four (necessary for OpenGL happiness)
            gm.width += 4 - (gm.width%4);
            gm.height += 4 - (gm.height%4);
        }

        gm.data = new byte[gm.width*gm.height];

        if (defaultFill != 0)
            gm.fill(defaultFill);

        return gm;
    }

    protected GridMap()
    {
    }

    public void clear()
    {
        fill(defaultFill);
    }

    /** Write the provided value to every grid element **/
    public void fill(int v)
    {
        byte bv = (byte) v;

        for (int i = 0; i < data.length; i++)
            data[i] = bv;
    }

    /** Map every current value of the gridmap to a new value. The values array should be 255. **/
    public void map(byte values[])
    {
        for (int i = 0; i < data.length; i++)
            data[i] = values[data[i]&0xff];
    }

    /** Modify data with all new values. **/
    public void setData(byte values[])
    {
        for (int i = 0; i < data.length && i < values.length; i++)
            data[i] = values[i];
    }

    public GridMap copy()
    {
        GridMap gm = new GridMap();
        gm.x0 = x0;
        gm.y0 = y0;
        gm.metersPerPixel = metersPerPixel;
        gm.width = width;
        gm.height = height;
        gm.data = new byte[data.length];
        gm.defaultFill = defaultFill;

        for (int i = 0; i < data.length; i++)
            gm.data[i] = data[i];

        return gm;
    }

    /** return the average value of the gridmap **/
    public double average()
    {
        double sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += data[i];
        }

        return sum / data.length;
    }

    static double sq(double v)
    {
        return v*v;
    }

    static int sgn(double v)
    {
        if (v > 0)
            return 1;
        if (v < 0)
            return -1;
        return 0;
    }

    /** memset **/
    static void arraySet(byte d[], int offset, int length, byte value)
    {
        for (int i = 0; i < length; i++)
            d[i+offset] = value;
    }

    /** memmove (i.e., areas can overlap **/
    static void arrayMove(byte d[], int srcoffset, int destoffset, int len)
    {
        if (destoffset > srcoffset) {
            for (int i = len-1; i >= 0; i--)
                d[destoffset + i] = d[srcoffset + i];
        } else {
            for (int i = 0; i < len; i++)
                d[destoffset + i] = d[srcoffset + i];
        }
    }

    /** Recenter the gridmap, translating the data as necessary so
     * that cx0 and cy0 are near new center. The recenter operation is
     * skipped if the map would be translated by less than
     * maxDistance.
     **/
    public void recenter(double cx0, double cy0, double maxDistance)
    {
        double cx = x0 + (width + .5) * metersPerPixel / 2;
        double cy = y0 + (height + .5) * metersPerPixel / 2;

        double distanceSq = sq(cx - cx0) + sq(cy - cy0);
        double pixelsPerMeter = 1.0 / metersPerPixel;

        if (distanceSq < sq(maxDistance))
            return;

        // how far (in pixels) to move. We round to an exact pixel
        // boundary.  e.g., dx = source - dest
        int dx = (int) ((cx0 - cx) * pixelsPerMeter);
        int dy = (int) ((cy0 - cy) * pixelsPerMeter);

        if (dy >= 0) {
            for (int dest = 0; dest < height; dest++) {
                int src = dest + dy;
                if (src < 0 || src >= height) {
                    arraySet(data, dest*width, width, defaultFill);
                    continue;
                }

                if (dx >= 0) {
                    int sz = Math.max(0, width - dx);
                    arrayMove(data, src*width + dx, dest*width, sz);
                    arraySet(data, dest*width + sz, width - sz, defaultFill);
                } else {
                    int sz = Math.max(0, width + dx);
                    arrayMove(data, src*width, dest*width - dx, sz);
                    arraySet(data, dest*width, width - sz, defaultFill);
                }
            }
        } else {
            for (int dest = height-1; dest >= 0; dest--) {
                int src = dest + dy;

                if (src < 0 || src >= height) {
                    arraySet(data, dest*width, width, defaultFill);
                    continue;
                }

                if (dx >= 0) {
                    int sz = Math.max(0, width - dx);
                    arrayMove(data, src*width + dx, dest*width, sz);
                    arraySet(data, dest*width + sz, width - sz, defaultFill);
                } else {
                    int sz = Math.max(0, width + dx);
                    arrayMove(data, src*width, dest*width - dx, sz);
                    arraySet(data, dest*width, width - sz, defaultFill);
                }
            }
        }

        cx += dx * metersPerPixel;
        cy += dy * metersPerPixel;
        x0 += dx * metersPerPixel;
        y0 += dy * metersPerPixel;
    }

    public double evaluatePath(ArrayList<double[]> xys, boolean negativeOn255)
    {
        double cost = 0;

        for (int i = 0; i + 1 < xys.size(); i++) {
            double thisCost = evaluatePath(xys.get(i), xys.get(i+1), negativeOn255);
            if (thisCost < 0)
                return thisCost;
            cost += thisCost;
        }

        return cost;
    }

    /** Evaluate the integral of the cost along the path from xy0 to
     * xy1. If negativeOn255 is set, -1 will be returned if the path
     * goes through a cell whose value is 255. **/
    public double evaluatePath(double xy0[], double xy1[], boolean negativeOn255)
    {
        // we'll microstep at 0.25 pixels. this isn't exact but it's pretty darn close.
        double stepSize = metersPerPixel * 0.25;

        double dist = Math.sqrt(sq(xy0[0]-xy1[0]) + sq(xy0[1]-xy1[1]));

        int nsteps = ((int) (dist / stepSize)) + 1;

        double cost = 0;

        for (int i = 0; i < nsteps; i++) {
            double alpha = ((double) i) / nsteps;
            double x = alpha*xy0[0] + (1-alpha)*xy1[0];
            double y = alpha*xy0[1] + (1-alpha)*xy1[1];

            int v = getValue(x,y);
            if (negativeOn255 && v==255)
                return -1;

            cost += v;
        }

        // normalize correctly for distance.
        return cost * dist / nsteps;
    }

    /*
      public double evaluatePath(double xy0[], double xy1[])
      {
      // we'll move (xnow,ynow) from xy0 to xy1. It will always be
      // exactly on the desired line.
      double xnow = xy0[0], ynow = xy0[1];

      int ix1 = (int) ((xy1[0] - x0) * pixelsPerMeter);
      int iy1 = (int) ((xy1[0] - y0) * pixelsPerMeter);

      while (xnow != xy1[0] || ynow != xy1[1]) {

      // direction to go
      double dx = xy1[0] - xnow;
      double dy = xy1[1] - ynow;

      // which grid cell are we in now?
      int ix = (int) ((xnow - x0) * pixelsPerMeter);
      int iy = (int) ((ynow - y0) * pixelsPerMeter);

      // easy case: we've arrived in the desired grid cell
      if (ix0 == ix1 && iy0 == iy1) {
      double dist = Math.sqrt(sq(xy0[0]-xy1[0]) + sq(xy0[1]-xy1[1]));
      return dist * getValueIndexSafe(ix0, iy0);
      }

      // Okay, we need to go to the next grid cell. What is the
      // smallest change in the direction of xy1 that will result in
      // us moving into another bucket?
      double distx = (ix + sgn(dx))*pixelsPerMeter + x0 - xnow;
      double disty = (iy + sgn(dy))*pixelsPerMeter + y0 - ynow;

      double
      }


      return 0;
      }
    */

    /** Return a float image with 0 mapped to 0 and 255 mapped to 1.0 **/
    public FloatImage makeFloatImage()
    {
        FloatImage fim = new FloatImage(width, height);

        for (int i = 0; i < data.length; i++) {
            fim.d[i] = ((data[i]&0xff)/255.0f);
        }

        return fim;
    }

    /** Create a buffered image, mapping values to grayscale. **/
    public BufferedImage makeBufferedImage()
    {
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        byte imdata[] = ((DataBufferByte) (im.getRaster().getDataBuffer())).getData();
        for (int i = 0; i < data.length; i++)
            imdata[i] = data[i];

        return im;
    }

    /** Create a buffered image, using the specified color map. The
     * image has no alpha channel.
     **/
    public BufferedImage makeBufferedImageRGB(int rgb[])
    {
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int imdata[] = ((DataBufferInt) (im.getRaster().getDataBuffer())).getData();
        for (int i = 0; i < data.length; i++)
            imdata[i] = rgb[data[i]&0xff];

        return im;
    }

    /** Create a buffered image, using the specified color map. Note
     * that you must set the alpha value to non-zero for it to show
     * up, e.g., 0xAARRGGBB.
     **/
    public BufferedImage makeBufferedImageARGB(int argb[])
    {
        BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int imdata[] = ((DataBufferInt) (im.getRaster().getDataBuffer())).getData();
        for (int i = 0; i < data.length; i++)
            imdata[i] = argb[data[i]&0xff];

        return im;
    }

    public void drawCircle(double cx, double cy, double r, byte fill)
    {
        double pixelsPerMeter = 1.0 / metersPerPixel;

        int ix0 = (int) ((cx - r - x0) * pixelsPerMeter);
        int ix1 = (int) ((cx + r - x0) * pixelsPerMeter);
        int iy0 = (int) ((cy - r - y0) * pixelsPerMeter);
        int iy1 = (int) ((cy + r - y0) * pixelsPerMeter);

        for (int iy = iy0; iy <= iy1; iy++) {
            if (iy < 0 || iy >= height)
                continue;

            for (int ix = ix0; ix <= ix1; ix++) {
                if (ix < 0 || ix >= width)
                    continue;

                double x = x0 + (ix + .5)*metersPerPixel;
                double y = y0 + (iy + .5)*metersPerPixel;

                double d2 = (x - cx)*(x-cx) + (y - cy)*(y - cy);
                if (d2 <= (r*r))
                    data[iy*width + ix] = fill;
            }
        }
    }

    public void drawLine(double xa, double ya, double xb, double yb, byte fill)
    {
        double dist = Math.sqrt(sq(xb-xa) + sq(yb-ya));
        int nsteps = (int) (dist / metersPerPixel + 1);
        double pixelsPerMeter = 1.0 / metersPerPixel;

        for (int i = 0; i < nsteps; i++) {
            double alpha = ((double) i)/nsteps;
            double x = xa*alpha + xb*(1-alpha);
            double y = ya*alpha + yb*(1-alpha);

            int ix = (int) ((x - x0) * pixelsPerMeter);
            int iy = (int) ((y - y0) * pixelsPerMeter);

            if (ix >= 0 && ix < width && iy >= 0 && iy < height)
                data[iy*width + ix] = fill;
        }
    }

    /** Get the minimum x and y coordinates **/
    public double[] getXY0()
    {
        return new double[] { x0, y0 };
    }

    /** Get the maximum x and y coordinates **/
    public double[] getXY1()
    {
        return new double[] { x0 + width*metersPerPixel, y0 + height*metersPerPixel };
    }

    public void setValue(double x, double y, byte v)
    {
        int ix = (int) ((x - x0) / metersPerPixel);
        int iy = (int) ((y - y0) / metersPerPixel);

        setValueIndexSafe(ix, iy, v);
    }

    public void setValueIndex(int ix, int iy, byte v)
    {
        data[iy*width + ix] = v;
    }

    public void setValueIndexSafe(int ix, int iy, byte v)
    {
        if (iy < 0 || ix < 0 || ix >= width || iy >= height)
            return;

        data[iy*width + ix] = v;
    }

    public int getValue(double x, double y)
    {
        int ix = (int) ((x - x0) / metersPerPixel);
        int iy = (int) ((y - y0) / metersPerPixel);

        return getValueIndexSafe(ix, iy, defaultFill);
    }

    public int getValueIndex(int ix, int iy)
    {
        return data[iy*width + ix]&0xff;
    }

    public int getValueIndexSafe(int ix, int iy)
    {
        return getValueIndexSafe(ix, iy, defaultFill);
    }

    public int getValueIndexSafe(int ix, int iy, int def)
    {
        if (iy < 0 || ix < 0 || ix >= width || iy >= height)
            return def;

        return data[iy*width + ix]&0xff;
    }

    /** Does the grid cell at (ix,iy) have a neighbor whose value is
     * v? ix, iy are in pixel coordinates. **/
    public boolean hasNeighbor(int ix, int iy, byte v)
    {
        if (iy > 0) {
            if (ix > 0)
                if (data[(iy-1)*width+(ix-1)]==v)
                    return true;
            if (data[(iy-1)*width+ix]==v)
                return true;
            if (ix+1 < width)
                if (data[(iy-1)*width+(ix+1)]==v)
                    return true;
        }

        if (ix > 0)
            if (data[iy*width+ix-1]==v)
                return true;
        if (ix+1 < width)
            if (data[iy*width+ix+1]==v)
                return true;

        if (iy+1 < height) {
            if (ix > 0)
                if (data[(iy+1)*width+(ix-1)]==v)
                    return true;
            if (data[(iy+1)*width+ix]==v)
                return true;
            if (ix+1 < width)
                if (data[(iy+1)*width+(ix+1)]==v)
                    return true;
        }
        return false;
    }

    /** Find all grid cells with value v that have a neighbor that is
     * not v. Clear all other cells to zero.
     **/
    public GridMap edges(byte v)
    {
        GridMap gm = copy();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                if (data[y*width+x] != v) {
                    gm.data[y*width+x] = 0;
                    continue;
                }

                if (!hasNeighbor(x, y, (byte) 0))
                    gm.data[y*width + x] = 0;
            }
        }

        return gm;
    }

    /** Set all neighbors of grid cells with value 'v' to v. Repeat
     * this process 'iterations' times.
     **/
    public GridMap dilate(byte v, int iterations)
    {
        GridMap src = this;
        GridMap dest = null;

        for (int iter = 0; iter < iterations; iter++) {

            dest = src.copy();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (dest.data[y*width+x]==v)
                        continue;
                    if (src.hasNeighbor(x, y, v))
                        dest.data[y*width+x] = v;
                }
            }

            src = dest;
        }

        if (dest == null)
            return src.copy();

        return dest;
    }

    /** Multiply every value in the grid by 's'. **/
    public void scale(double s)
    {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v0 = data[y*width+x]&0xff;

                data[y*width+x] = (byte) (v0 * s);
            }
        }
    }

    /** Subtract from every value the value 'v', stopping at zero. **/
    public void subtract(int v)
    {
        for (int i = 0; i < data.length; i++) {
            int w = data[i]&0xff;
            w -= v;
            if (w < 0)
                w = 0;
            data[i] = (byte) w;
        }
    }

    public boolean isCompatible(GridMap gm)
    {
        return !(gm.x0 != x0 || gm.y0 != y0 ||
                 gm.metersPerPixel != metersPerPixel ||
                 gm.width != width || gm.height != height);
    }

    public void plusEquals(GridMap gm)
    {
        assert(isCompatible(gm));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v0 = data[y*width+x]&0xff;
                int v1 = gm.data[y*width+x]&0xff;

                data[y*width+x] = (byte) (v0 + v1);
            }
        }
    }

    public void maxEquals(GridMap gm)
    {
        assert(isCompatible(gm));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v0 = data[y*width+x]&0xff;
                int v1 = gm.data[y*width+x]&0xff;

                data[y*width+x] = (byte) Math.max(v0, v1);
            }
        }
    }

    /** Starting at pixel coordinates (ix,iy) do a flood fill over all
     * pixels whose integer value[0,255] is less than tolerance,
     * setting those pixels to 'v'. **/
    public void fill(int ix, int iy, byte v, int tolerance)
    {
        fillDown(ix, ix, iy, v, tolerance);
    }

    /** the span [ix0,ix1] on row iy is a set of pixels that neighbors
        matching pixels in the row above.  For each span of matching
        pixels in this row, recurse below. If we find a pixel above us
        that needs to be set, recurse upwards.
    **/
    void fillDown(int ix0, int ix1, int iy, byte v, int tolerance)
    {
        if (iy >= height)
            return;

        // grow horizontally within this row.
        while (ix0 > 0 && (data[iy*width + ix0]&0xff) < tolerance)
            ix0--;

        while (ix1+1 < width && (data[iy*width + ix1]&0xff) < tolerance)
            ix1++;

        // consider all pixels in this horizontal span.
        int start = -1;

        if ((data[iy*width+ix0]&0xff) < tolerance)
            start = ix0;

        boolean dirty = false;

        for (int ix = ix0; ix <= ix1; ix++) {
            if ((data[iy*width+ix]&0xff) < tolerance) {
                data[iy*width+ix] = v;
                dirty = true;
                if (start < 0) { // start a new run?
                    start = ix;
                }
            } else {
                if (start >= 0) { // end a run?
                    fillDown(start, ix-1, iy+1, v, tolerance);
                    start = -1;
                }
            }
        }

        if (start >= 0)
            fillDown(start, ix1, iy+1, v, tolerance);

        if (iy > 0 && dirty) {
            fillUp(ix0, ix1, iy-1, v, tolerance);
        }
    }

    /** the span [ix0,ix1] on row iy is a set of pixels that neighbors
        matching pixels in the row above.  For each span of matching
        pixels in this row, recurse below. If we find a pixel above us
        that needs to be set, recurse upwards.
    **/
    void fillUp(int ix0, int ix1, int iy, byte v, int tolerance)
    {
        if (iy < 0)
            return;

        // grow horizontally within this row.
        while (ix0 > 0 && (data[iy*width + ix0]&0xff) <= tolerance)
            ix0--;

        while (ix1+1 < width && (data[iy*width + ix1]&0xff) <= tolerance)
            ix1++;

        // consider all pixels in this horizontal span.
        int start = -1;

        if ((data[iy*width+ix0]&0xff) <= tolerance)
            start = ix0;

        boolean dirty = false;

        for (int ix = ix0; ix <= ix1; ix++) {
            if ((data[iy*width+ix]&0xff) < tolerance) {
                data[iy*width+ix] = v;
                dirty = true;
                if (start < 0) { // start a new run?
                    start = ix;
                }

            } else {
                if (start >= 0) { // end a run?
                    fillUp(start, ix-1, iy-1, v, tolerance);
                    start = -1;
                }
            }
        }

        if (start >= 0)
            fillUp(start, ix1, iy-1, v, tolerance);


        if (iy+1 < height && dirty) {
            fillDown(ix0, ix1, iy+1, v, tolerance);
        }
    }

    /** Linear mapping: index i corresponds to a distance of i*metersPerPixel **/
    public static class LUT
    {
        public double metersPerPixel;

        public int lut[];
    }

    /** Make a lut that has value of 255 for the first
        cliffDistMeters, then drops of as 255*exp(-expdecay*dist^2).
        The whole lut will be scaled by 'scale'.

        v = 255*scale*exp(-max(0, dist - cliff)^2*expDecayMSq)
    **/
    public LUT makeExponentialLUT(double scale, double cliffDistMeters, double expDecayMSq)
    {
        LUT lut = new LUT();
        lut.metersPerPixel = metersPerPixel;

        assert(expDecayMSq > 0);

        // how long does the LUT need to be for the last element to decay to 0?
        // solve expression for distance, setting v = 1. Then round 'd' up.
        double maxDistance = cliffDistMeters + Math.sqrt(Math.log(255*scale)/expDecayMSq);

        int length = (int) (maxDistance / lut.metersPerPixel + 1);

        lut.lut = new int[length];
        for (int i = 0; i < length; i++) {
            double d = Math.max(0, i * lut.metersPerPixel - cliffDistMeters);
            lut.lut[i] = (int) (255*scale*Math.exp(-d*d*expDecayMSq));
        }

        return lut;
    }

    /** Make a lut that starts at 255 and linearly declines to 0. Used for computing distance transforms. **/
    public LUT makeLinearReverseLUT()
    {
        LUT lut = new LUT();
        lut.metersPerPixel = metersPerPixel;

        lut.lut = new int[256];
        for (int i = 0; i <=255; i++) {
            lut.lut[i] = 255 - i;
        }

        return lut;
    }

    public LUT makeConstantLUT(int v, double width_meters)
    {
        LUT lut = new LUT();
        lut.metersPerPixel = width_meters;

        lut.lut = new int[1];
        lut.lut[0] = v;
        return lut;
    }

    public void drawDot(double x, double y, LUT lut)
    {
        drawRectangle(x, y, 0, 0, 0, lut);
    }

    public void drawLine(double x0, double y0, double x1, double y1, LUT lut)
    {
        double dx = (x1-x0), dy = (y1-y0);
        double length = Math.sqrt(dx*dx + dy*dy);

        drawRectangle((x0+x1)/2, (y0+y1)/2, length, 0, Math.atan2(y1-y0, x1-x0), lut);
    }

    public void drawRectangle(double cx, double cy,
                              double x_size, double y_size,
                              double theta,
                              LUT lut)
    {
        double pixelsPerMeter = 1.0 / metersPerPixel;

        double ux = Math.cos(theta), uy = Math.sin(theta);

        double lutRange = metersPerPixel * lut.lut.length;

        double x_bound = (x_size / 2.0 * Math.abs(ux) + y_size / 2.0 * Math.abs(uy)) + lutRange;
        double y_bound = (x_size / 2.0 * Math.abs(uy) + y_size / 2.0 * Math.abs(ux)) + lutRange;

        // lots of overdraw for high-aspect rectangles around 45 degrees.
        int ix0 = clamp((int) ((cx - x_bound - x0)*pixelsPerMeter), 0, width - 1);
        int ix1 = clamp((int) ((cx + x_bound - x0)*pixelsPerMeter), 0, width - 1);

        int iy0 = clamp((int) ((cy - y_bound - y0)*pixelsPerMeter), 0, height - 1);
        int iy1 = clamp((int) ((cy + y_bound - y0)*pixelsPerMeter), 0, height - 1);

        // Each pixel will be evaluated based on the distance to the
        // center of that pixel.
        double y = y0 + (iy0+.5)*metersPerPixel;

        double lutPixelsPerMeter = 1.0 / lut.metersPerPixel;

        for (int iy = iy0; iy <= iy1; iy++) {

            double x = x0 + (ix0+.5)*metersPerPixel;

            for (int ix = ix0; ix <= ix1; ix++) {

                // distances from query point to center of rectangle
                double dx = x - cx, dy = y - cy;

                // how long are the projections of the vector (dx,dy) onto the two principle
                // components of the rectangle? How much longer are they than the dimensions
                // of the rectangle?
                double c1 = Math.abs(dx * ux + dy * uy) - (x_size / 2);
                double c2 = Math.abs(- dx * uy + dy * ux) - (y_size / 2);

                // if the projection length is < 0, we're *inside* the rectangle.
                c1 = Math.max(0, c1);
                c2 = Math.max(0, c2);

                double dist = Math.sqrt(c1*c1 + c2*c2);

                int lutIdx = (int) (dist * lutPixelsPerMeter + .5);

                if (lutIdx < lut.lut.length) {
                    int idx = iy*width + ix;
                    data[idx] = (byte) Math.max(data[idx]&0xff, lut.lut[lutIdx]);
                }

                x += metersPerPixel;
            }

            y += metersPerPixel;
        }
    }

    static int clamp(int v, int min, int max)
    {
        if (v > max)
            return max;
        if (v < min)
            return min;
        return v;
    }

    public GridMap decimateMax(int factor)
    {
        int newwidth = width / factor;
        int newheight = width / factor;

        GridMap gm = GridMap.makePixels(x0, y0, newwidth, newheight, metersPerPixel*factor, (byte) 0, true);
        gm.defaultFill = defaultFill;

        // loop over input rows
        for (int iy = 0; iy < height; iy++) {
            // which output row should this affect?
            int oy = iy/factor;

            // loop over output columns
            for (int ox = 0; ox < gm.width; ox++) {
                // loop over input columns for the current output column.
                int maxv = 0;
                int maxdx = Math.min(factor, width - ox*factor);
                for (int dx = 0; dx < maxdx; dx++) {
                    maxv = Math.max(maxv, data[iy*width + ox*factor + dx]&0xff);
                }
                // update output column
                gm.data[oy*gm.width + ox] = (byte) Math.max(gm.data[oy*gm.width + ox]&0xff, maxv);
            }
        }

        return gm;
    }

    /** Make a new gridmap where each cell at (x,y) is the max of the
     * 2x2 square at (x,y).
     **/
    public GridMap max4()
    {
        GridMap gm = copy();
        for (int iy = 0; iy + 1 < height; iy++) {
            for (int ix = 0; ix + 1 < width; ix++) {
                int v = Math.max(Math.max(data[iy*width+ix]&0xff,
                                          data[iy*width+ix+1]&0xff),
                                 Math.max(data[(iy+1)*width+ix]&0xff,
                                          data[(iy+1)*width+ix+1]&0xff));
                gm.data[iy*width+ix] = (byte) v;
            }
            gm.data[iy*width + width-1] = (byte) Math.max(data[(iy)*width + width-1]&0xff,
                                                          data[(iy+1)*width + width-1]&0xff);
        }

        for (int ix = 0; ix + 1 < width; ix++) {
            gm.data[(height-1)*width + ix] = (byte) Math.max(data[(height-1)*width + ix]&0xff,
                                                             data[(height-1)*width + ix+1]&0xff);
        }

        return gm;
    }

    /** Count how many of te points land in a grid cell whose value is at least 'thresh' **/
    public int scoreThreshold(ArrayList<double[]> points,
                              double tx, double ty, double theta,
                              int thresh)
    {
        double ct = Math.cos(theta), st = Math.sin(theta);
        double pixelsPerMeter = 1.0 / metersPerPixel;

        int score = 0;

        for (int pidx = 0; pidx < points.size(); pidx++) {

            // project the point
            double p[] = points.get(pidx);
            double x = p[0]*ct - p[1]*st + tx;
            double y = p[0]*st + p[1]*ct + ty;

            // (ix0, iy0) are the coordinates in distdata that
            // correspond to scores[0]. It's the (nominal) upper-left
            // corner of our search window.

            int ix = ((int) ((x - x0)*pixelsPerMeter));
            int iy = ((int) ((y - y0)*pixelsPerMeter));

            if (ix >= 0 && ix < width && iy >=0 && iy < height)
                if ((data[iy*width + ix]&0xff) > thresh)
                    score++;

        }
        return score;
    }

    public int score(ArrayList<double[]> points,
                     double tx, double ty, double theta)
    {
        double ct = Math.cos(theta), st = Math.sin(theta);
        double pixelsPerMeter = 1.0 / metersPerPixel;

        int score = 0;

        for (int pidx = 0; pidx < points.size(); pidx++) {

            // project the point
            double p[] = points.get(pidx);
            double x = p[0]*ct - p[1]*st + tx;
            double y = p[0]*st + p[1]*ct + ty;

            // (ix0, iy0) are the coordinates in distdata that
            // correspond to scores[0]. It's the (nominal) upper-left
            // corner of our search window.

            int ix = ((int) ((x - x0)*pixelsPerMeter));
            int iy = ((int) ((y - y0)*pixelsPerMeter));

            if (ix >= 0 && ix < width && iy >=0 && iy < height)
                score += data[iy*width + ix]&0xff;

        }
        return score;
    }

    /**
       Project all the points (non-destructively) by the rotation
       theta and translate them according to (tx0 + i*metersPerPixel,
       ty0 + j*metersPerPixel). For each projected point, add the
       values of the lookup table at that point. Repeat this process
       over all i \in [0, txDim] and all j \in [0, tyDim].
    **/
    public IntArray2D scores2D(ArrayList<double[]> points,
                               double tx0, int txDim,
                               double ty0, int tyDim,
                               double theta)
    {
        IntArray2D scores = new IntArray2D(tyDim, txDim);

        double ct = Math.cos(theta), st = Math.sin(theta);
        double pixelsPerMeter = 1.0 / metersPerPixel;

        // Evaluate each point for a fixed rotation but variable
        // translation
        for (int pidx = 0; pidx < points.size(); pidx++) {

            // project the point
            double p[] = points.get(pidx);
            double x = p[0]*ct - p[1]*st + tx0;
            double y = p[0]*st + p[1]*ct + ty0;

            // (ix0, iy0) are the coordinates in distdata that
            // correspond to scores[0]. It's the (nominal) upper-left
            // corner of our search window.

            int ix0 = ((int) ((x - x0)*pixelsPerMeter));
            int iy0 = ((int) ((y - y0)*pixelsPerMeter));

            // compute the intersection of the box
            // (ix0,iy0)->(ix0+ixdim-1,iy0+iydim-1) and the box
            // (0,0)->(width-1, height-1). This will be our actual
            // search window.
            int bx0 = Math.max(ix0, 0);
            int by0 = Math.max(iy0, 0);

            int bx1 = Math.min(ix0 + txDim - 1, width-1);
            int by1 = Math.min(iy0 + tyDim - 1, height-1);

            for (int iy = by0; iy <= by1; iy++) {

                int sy = iy - iy0; // y coordinate in scores[]

                for (int ix = bx0; ix <= bx1; ix++) {

                    int lutval = data[iy*width + ix]&0xff;
                    int sx = ix - ix0;

                    scores.plusEquals(sy, sx, lutval);
                }
            }
        }

        return scores;
    }

    /** Evaluate scores2D over a range of thetas, theta = theta0 +
     * thetaStep*i, for i=[0, thetaDim]
     **/
    public  IntArray2D[] scores3D(ArrayList<double[]> points,
                                  double tx0, int txDim,
                                  double ty0, int tyDim,
                                  double theta0, double thetaStep, int thetaDim)
    {
        IntArray2D scores[] = new IntArray2D[thetaDim];

        for (int i = 0; i < thetaDim; i++) {
            double theta = theta0 + i*thetaStep;

            scores[i] = scores2D(points, tx0, txDim, ty0, tyDim, theta);
        }

        return scores;
    }

    /** Get 8-connected nodes around point xy with cost under maxCost
      * @param xy      - Continuous-domain point around which to find
      *                  connected nodes
      * @param maxCost - Maximum cost for which a node can be considered valid
      **/
    public byte[] getConnectedWithin(double[] xy, int maxCost)
    {
        double pixelsPerMeter = 1.0 / metersPerPixel;
        int px = (int) ((xy[0] - x0) * pixelsPerMeter);
        int py = (int) ((xy[1] - y0) * pixelsPerMeter);
        if (px < 0 || px >= width || py < 0 || py >= height)
            return null;

        UnionFindSimple uf = new UnionFindSimple(data.length);

        // We connect the following points around pixel 'o':
        // . . x
        // . o x
        // . x x

        // To avoid checking bounds per neighbor, we adjust the
        // bounds of x and y
        for (int y=1; y < height-1; y++)
        {
            for (int x=0; x < width-1; x++)
            {
                int a = y*width + x;

                if ((((int) data[a]) & 0xFF) > maxCost)
                    continue;

                int b;
                // x+1, y-1
                b = (y-1)*width + (x+1);
                if ((((int) data[b]) & 0xFF) <= maxCost)
                    uf.connectNodes(uf.getRepresentative(a),
                                    uf.getRepresentative(b));

                // x+1, y
                b = (y)*width + (x+1);
                if ((((int) data[b]) & 0xFF) <= maxCost)
                    uf.connectNodes(uf.getRepresentative(a),
                                    uf.getRepresentative(b));

                // x+1, y+1
                b = (y+1)*width + (x+1);
                if ((((int) data[b]) & 0xFF) <= maxCost)
                    uf.connectNodes(uf.getRepresentative(a),
                                    uf.getRepresentative(b));

                // x  , y+1
                b = (y+1)*width + (x);
                if ((((int) data[b]) & 0xFF) <= maxCost)
                    uf.connectNodes(uf.getRepresentative(a),
                                    uf.getRepresentative(b));
            }
        }

        // get id of union around robot
        int cid = uf.getRepresentative(py*width + px);

        byte[] res = new byte[data.length];
        for (int i=0; i < data.length; i++)
        {
            if (uf.getRepresentative(i) == cid)
                res[i] = (byte) 0x1;
        }

        return res;
    }

    public void filterFactoredCenteredMax(float fhoriz[], float fvert[])
    {
        byte r[] = new byte[data.length];

        // do horizontal
        for (int y = 0; y < height; y++) {
            april.image.SigProc.convolveSymmetricCenteredMax(data, y*width, width, fhoriz, r, y*width);
        }

        // do vertical
        byte tmp[] = new byte[height];  // the column before convolution
        byte tmp2[] = new byte[height]; // the column after convolution.

        for (int x = 0; x < width; x++) {

            // copy the column out for locality.
            for (int y = 0; y < height; y++)
                tmp[y] = r[y*width + x];

            SigProc.convolveSymmetricCenteredMax(tmp, 0, height, fvert, tmp2, 0);

            for (int y = 0; y < height; y++)
                r[y*width + x] = tmp2[y];
        }

        this.data = r;
    }
}
