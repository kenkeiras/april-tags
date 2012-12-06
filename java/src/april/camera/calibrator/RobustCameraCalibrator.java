package april.camera.calibrator;

import java.awt.image.*;
import java.awt.Color;
import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.tag.*;
import april.vis.*;

public class RobustCameraCalibrator
{
    CameraCalibrationSystem cal;

    VisWorld worlds[];
    VisLayer layers[];
    VisCanvas vc;
    int numUsedLayers;

    TagFamily tf;
    TagMosaic tm;
    double metersPerTag;

    int minCol = -1, maxCol = -1, minRow = -1, maxRow = -1;

    double Tvis[][] = new double[][] { {  0,  0,  1,  0 },
                                       { -1,  0,  0,  0 } ,
                                       {  0, -1,  0,  0 } ,
                                       {  0,  0,  0,  1 } };

    public RobustCameraCalibrator(List<CalibrationInitializer> initializers,
                                  TagFamily tf, double metersPerTag, boolean gui)
    {
        this.tf = tf;
        this.tm = new TagMosaic(tf, metersPerTag);
        this.metersPerTag = metersPerTag;

        cal = new CameraCalibrationSystem(initializers, tf, metersPerTag);

        vc = new VisCanvas();
        worlds = new VisWorld[initializers.size()];
        layers = new VisLayer[initializers.size()];

        for (int i = 0; i < initializers.size(); i++) {
            String name = String.format("Subsystem %d", i);
            VisWorld vw = new VisWorld();
            VisLayer vl = new VisLayer(name, vw);

            vl.layerManager = new GridLayerManager(0, i, 1, initializers.size());

            int gray = 20 + 10*i;
            vl.backgroundColor = new Color(gray, gray, gray);

            DefaultCameraManager cameraManager = (DefaultCameraManager) vl.cameraManager;
            cameraManager.interfaceMode = 2.5;

            VisCameraManager.CameraPosition pos = cameraManager.getCameraTarget();
            pos.eye    = new double[] { 0.1, 0.0, 2.0 };
            pos.lookat = new double[] { 0.1, 0.0, 0.0 };
            pos.up     = new double[] { 1.0, 0.0, 0.0 };
            pos.perspectiveness = 0;
            //pos.eye    = new double[] { 1.2, 0.0, 0.5 };
            //pos.lookat = new double[] { 0.2, 0.0, 0.0 };
            //pos.up     = new double[] {-0.4, 0.0, 0.9 };
            cameraManager.goUI(pos);
            cameraManager.setDefaultPosition(pos.eye, pos.lookat, pos.up);

            VzGrid.addGrid(vw, new VzGrid(new VzLines.Style(new Color(128, 128, 128, 128), 1)));
            vw.getBuffer("grid").setDrawOrder(-10001);
            vw.getBuffer("grid-overlay").setDrawOrder(-10000);

            worlds[i] = vw;
            layers[i] = vl;

            vc.addLayer(vl);
        }

        numUsedLayers = initializers.size();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // initialize parameters

    ////////////////////////////////////////////////////////////////////////////////
    // add imagery

    public void addOneImageSet(List<BufferedImage> newImages,
                               List<List<TagDetection>> newDetections)
    {
        cal.addSingleImageSet(newImages, newDetections);

        for (List<TagDetection> detections : newDetections) {
            // update observed mosaic bounds
            for (TagDetection d : detections) {
                minCol = Math.min(minCol, this.tm.getColumn(d.id));
                maxCol = Math.max(maxCol, this.tm.getColumn(d.id));
                minRow = Math.min(minRow, this.tm.getRow(d.id));
                maxRow = Math.max(maxRow, this.tm.getRow(d.id));
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    // graph optimization

    private class GraphStats
    {
        public double MRE; // mean reprojection error
        public double MSE; // mean-squared reprojection error
    }

    public List<GraphStats> iterateUntilConvergence()
    {
        return null;
    }

    public List<GraphStats> iterateWithConvergenceAndReinitalization()
    {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // rendering code

    public VisCanvas getVisCanvas()
    {
        return vc;
    }

    public void draw()
    {
        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        /*
        Set<Integer> uniqueRoots = new TreeSet<Integer>();

        for (CameraCalibrationSystem.CameraWrapper cam : cameras)
            uniqueRoots.add(cam.rootNumber);

        Integer roots[] = uniqueRoots.toArray(new Integer[0]);
        */

        for (int i = 0; i < worlds.length; i++)
            drawSubsystem(cameras, mosaics, worlds[i], layers[i], i);

        updateLayerManagers(cameras);
    }

    private void drawSubsystem(List<CameraCalibrationSystem.CameraWrapper> cameras,
                               List<CameraCalibrationSystem.MosaicWrapper> mosaics,
                               VisWorld vw, VisLayer vl,
                               int root)
    {
        VisWorld.Buffer vb;

        vb = vw.getBuffer("Cameras");
        for (CameraCalibrationSystem.CameraWrapper cam : cameras)
        {
            if (cam.rootNumber != root)
                continue;

            double CameraToRoot[][] = LinAlg.xyzrpyToMatrix(cam.CameraToRootXyzrpy);

            vb.addBack(new VisChain(Tvis,
                                    CameraToRoot,
                                    LinAlg.scale(0.05, 0.05, 0.05),
                                    new VzAxes()));
        }
        vb.swap();

        // compute mosaic border
        double XY0[] = this.tm.getPositionMeters(minCol - 0.5, minRow - 0.5);
        double XY1[] = this.tm.getPositionMeters(maxCol + 0.5, maxRow + 0.5);

        vb = vw.getBuffer("Mosaics");
        for (int mosaicIndex = 0; mosaicIndex < mosaics.size(); mosaicIndex++)
        {
            CameraCalibrationSystem.MosaicWrapper mosaic = mosaics.get(mosaicIndex);

            double MosaicToRootXyzrpy[] = mosaic.MosaicToRootXyzrpys.get(root);

            if (MosaicToRootXyzrpy == null)
                continue;

            double MosaicToRoot[][] = LinAlg.xyzrpyToMatrix(MosaicToRootXyzrpy);

            Color c = ColorUtil.seededColor(mosaicIndex);
            vb.addBack(new VisChain(Tvis,
                                    MosaicToRoot,
                                    LinAlg.translate((XY0[0]+XY1[0])/2.0, (XY0[1]+XY1[1])/2.0, 0),
                                    new VzRectangle(XY1[0] - XY0[0],
                                                    XY1[1] - XY0[1],
                                                    new VzLines.Style(c, 2))));
        }
        vb.swap();
    }

    private void updateLayerManagers(List<CameraCalibrationSystem.CameraWrapper> cameras)
    {
        int usedLayers = 0;
        for (CameraCalibrationSystem.CameraWrapper cam : cameras)
            if (cam.cameraNumber == cam.rootNumber)
                usedLayers++;

        // did the number of layers in use change? if so, update layer managers
        if (usedLayers != numUsedLayers)
        {
            int usedSoFar = 0;
            for (CameraCalibrationSystem.CameraWrapper cam : cameras)
            {
                VisLayer vl = layers[cam.cameraNumber];

                // give in-use layers a real grid position
                if (cam.cameraNumber == cam.rootNumber) {
                    vl.layerManager = new GridLayerManager(0, usedSoFar,
                                                           1, usedLayers);
                    usedSoFar++;
                }
                // give unused layers a "hidden" layer position
                else {
                    double pos[] = new double[] { 1, 1, 0, 0 };
                    vl.layerManager = new DefaultLayerManager(vl, pos);
                }
            }

            numUsedLayers = usedLayers;
        }

    }

    ////////////////////////////////////////////////////////////////////////////////
    // file io code

    public void saveCalibrationAndImages(String basepath)
    {
    }
}
