package april.camera;

import java.awt.image.*;

public class BilinearRasterizer implements Rasterizer
{
    SyntheticView view;

    int indices[];
    int weights[];

    public BilinearRasterizer(SyntheticView view)
    {
        this.view = view;

        computeLookupTables();
    }

    /** Compute lookup tables for fast image rectification.
      */
    private void computeLookupTables()
    {
        int viewWidth   = view.getWidth();
        int viewHeight  = view.getHeight();
        int size        = viewWidth * viewHeight;

        int calWidth    = view.getCalibration().getWidth();
        int calHeight   = view.getCalibration().getHeight();

        indices = new int[size];
        weights = new int[size*4];

        int twoPow16 = (int) Math.pow(2, 16);

        for (int y_rp = 0; y_rp < viewHeight; y_rp++) {
            for (int x_rp = 0; x_rp < viewWidth; x_rp++) {

                double xy_rp[] = new double[] { x_rp, y_rp };

                double xy_dp[] = view.distort(xy_rp);

                int x_dp = (int) Math.floor(xy_dp[0]);
                int y_dp = (int) Math.floor(xy_dp[1]);

                double dx = xy_dp[0] - x_dp;
                double dy = xy_dp[1] - y_dp;

                int idx = -1;
                if (x_dp >= 0 && x_dp+1 < calWidth && y_dp >= 0 && y_dp+1 < calHeight)
                    idx = y_dp * calWidth + x_dp;

                indices[y_rp*viewWidth+ x_rp] = idx;

                if (idx == -1)
                    continue;

                // bilinear weights
                weights[4*(y_rp*viewWidth + x_rp) + 0] = (int) ((1-dx)*(1-dy) * twoPow16); // x0, y0
                weights[4*(y_rp*viewWidth + x_rp) + 1] = (int) ((  dx)*(1-dy) * twoPow16); // x1, y0
                weights[4*(y_rp*viewWidth + x_rp) + 2] = (int) ((1-dx)*(  dy) * twoPow16); // x0, y1
                weights[4*(y_rp*viewWidth + x_rp) + 3] = (int) ((  dx)*(  dy) * twoPow16); // x1, y1
            }
        }
    }

    /** Rectify an RGB BufferedImage using the lookup tables.
      */
    public BufferedImage rectifyImage(BufferedImage in)
    {
        int width = in.getWidth();
        int height = in.getHeight();

        int viewWidth   = view.getWidth();
        int viewHeight  = view.getHeight();

        int calWidth    = view.getCalibration().getWidth();
        int calHeight   = view.getCalibration().getHeight();

        // XXX This check can't handle rescaled imagery
        if (width != calWidth || height != calHeight)
            return null;

        BufferedImage out = new BufferedImage(viewWidth, viewHeight,
                                              BufferedImage.TYPE_INT_RGB);

        int _in[]  = ((DataBufferInt) (in.getRaster().getDataBuffer())).getData();
        int _out[] = ((DataBufferInt) (out.getRaster().getDataBuffer())).getData();

        for (int i=0; i < _out.length; i++) {

            int idx = indices[i];

            if (idx == -1)
                continue;

            int v00 = _in[idx];             // x0, y0
            int v10 = _in[idx + 1];         // x1, y0
            int v01 = _in[idx + width];     // x0, y1
            int v11 = _in[idx + width + 1]; // x1, y1

            int r00 = ((v00 >> 16) & 0xFF) * weights[4*i + 0];
            int r10 = ((v10 >> 16) & 0xFF) * weights[4*i + 1];
            int r01 = ((v01 >> 16) & 0xFF) * weights[4*i + 2];
            int r11 = ((v11 >> 16) & 0xFF) * weights[4*i + 3];

            int g00 = ((v00 >>  8) & 0xFF) * weights[4*i + 0];
            int g10 = ((v10 >>  8) & 0xFF) * weights[4*i + 1];
            int g01 = ((v01 >>  8) & 0xFF) * weights[4*i + 2];
            int g11 = ((v11 >>  8) & 0xFF) * weights[4*i + 3];

            int b00 = ((v00      ) & 0xFF) * weights[4*i + 0];
            int b10 = ((v10      ) & 0xFF) * weights[4*i + 1];
            int b01 = ((v01      ) & 0xFF) * weights[4*i + 2];
            int b11 = ((v11      ) & 0xFF) * weights[4*i + 3];

            int r = (r00 + r10 + r01 + r11) >> 16;
            int g = (g00 + g10 + g01 + g11) >> 16;
            int b = (b00 + b10 + b01 + b11) >> 16;

            _out[i] = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }

        return out;
    }
}
