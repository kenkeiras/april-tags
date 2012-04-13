package april.camera.cal;

import java.awt.image.*;

public class NearestNeighborRasterizer implements Rasterizer
{
    SyntheticView view;

    int indices[];

    public NearestNeighborRasterizer(SyntheticView view)
    {
        this.view = view;

        computeLookupTable();
    }

    private void computeLookupTable()
    {
        int viewWidth   = view.getWidth();
        int viewHeight  = view.getHeight();
        int size        = viewWidth * viewHeight;

        int calWidth    = view.getCalibration().getWidth();
        int calHeight   = view.getCalibration().getHeight();

        indices = new int[size];

        for (int y_rp = 0; y_rp < viewHeight; y_rp++) {
            for (int x_rp = 0; x_rp < viewWidth; x_rp++) {

                double xy_rp[] = new double[] { x_rp, y_rp };

                double xy_dp[] = view.distort(xy_rp);

                int x_dp = (int) Math.round(xy_dp[0]);
                int y_dp = (int) Math.round(xy_dp[1]);

                int idx = -1;
                if (x_dp >= 0 && x_dp+1 < calWidth && y_dp >= 0 && y_dp+1 < calHeight)
                    idx = y_dp * calWidth + x_dp;

                indices[y_rp*viewWidth+ x_rp] = idx;
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

            _out[i] = _in[idx];
        }

        return out;
    }
}
