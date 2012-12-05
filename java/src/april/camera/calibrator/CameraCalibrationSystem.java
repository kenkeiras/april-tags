package april.camera.calibrator;

/** This class attempts to handle camera and mosaic initialization,
    as well as sub-graph extraction when not enough information is known
    to perform nonlinear optimization on the entire system. Values improved
    by optimization should be updated so they are present when other terms
    can be initialized. Preferred initial values can be set by the user, but
    they will not be used until the parameter should be properly constrained
    by the tag detections present.

    // XXX it should be possible to initialize intrinsics even when we can't compute them

    Initialization occurs when all of the conditions (dependencies in brackets)
    for any of the actions below are true. These naturally form a topological ordering,
    so we can just check for each condition sequentially

    initialize camera intrinsics
    {
        not previously initialized
        initializer returns non-null ParameterizableCalibration
    }

    initialize mosaic extrinsics w.r.t. cameraX
    {
        not previously initialized
        cameraX's intrinsics have been initialized
        number of detections in cameraX image exceeds minimum
    }

    initialize camera extrinsics for cameraX w.r.t. cameraY
    {
        not previously initialized
        cameraX's intrinsics have been initialized
        cameraY's intrinsics have been initialized
        a mosiac exists with extrinsics w.r.t. cameraX
        a mosiac exists with extrinsics w.r.t. cameraY
    }
*/

public class CameraCalibrationGraph
{
    ////////////////////////////////////////////////////////////////////////////////
    // Data
    TagFamily           tf;
    TagMosaic           tm;
    double              metersPerTag;

    List<CameraWrapper> cameras;
    List<MosaicWrapper> mosaics;

    ////////////////////////////////////////////////////////////////////////////////
    // Classes
    public static class CameraWrapper
    {
        public int                      cameraNumber; // original camera index, zero-indexed
        public String                   name;         // safe to change at any time
        public CalibrationInitializer   initializer;

        public int width;
        public int height;

        // intrinsics
        public ParameterizableCalibration cal; // use get and set methods for parameter lists

        // extrinsics
        public boolean isPriviledgedCamera; // is this the camera at the origin?
        public double  CameraToGlobalXyzrpy[];
    }

    public static class MosaicWrapper
    {
        // one image and detection list per camera
        public List<BufferedImage>      imageSet;
        public List<List<TagDetection>> detectionSet;

        public HashMap<Integer,double[]> MosaicToCameraXyzrpys;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Methods
    public CameraCalibrationGraph(List<CalibrationInitializer> initializers,
                                  TagFamily tf, double metersPerTag)
    {
        this.tf = tf;
        this.tm = new TagMosaic(tf, metersPerTag);
        this.metersPerTag = metersPerTag;

        cameras = new ArrayList<CameraWrapper>();

        for (int cameraIndex = 0; cameraIndex < initializers.size(); cameraIndex++)
        {
            CameraWrapper cam = new CameraWrapper();
            cam.cameraNumber    = cameraIndex;
            cam.name            = String.format("camera%04d", cameraIndex);
            cam.initializer     = initializers.get(cameraIndex);

            // we don't know the dimensions until we see an image
            cam.width = -1;
            cam.height = -1;

            // we don't know the intrinsics
            cam.cal = null;

            // all cameras are initialized in separate frames at the origin
            // (they won't have extrinsics at first)
            cam.isPriviledgedCamera = true;
            cam.CameraToGlobalXyzrpy = new double[6];
        }

        // no frames yet
        mosaics = new ArrayList<MosaicWrapper>();
    }

    public void addSingleImageSet(List<BufferedImage> imageSet,
                                  List<List<TagDetection>> detectionSet)
    {
        // call the multi-set function with single-entry lists
        addMultipleImageSets(Arrays.asList(imageSet),
                             Arrays.asList(detectionSet));
    }

    public void addMultipleImageSets(List<List<BufferedImage>> imageSets,
                                     List<List<List<TagDetection>>> detectionSets)
    {
        assert(imageSets.size() == detectionSets.size());

        for (int setindex = 0; setindex < imageSets.size(); setindex++)
        {
            List<BufferedImage> imageSet = imageSets.get(setindex);
            List<List<TagDetection>> detectionSet = detectionSets.get(setindex);

            assert(imageSet.size() == cameras.size());
            assert(detectionSet.size() == cameras.size());

            // update/check camera width/height fields
            for (int cameraIndex = 0; cameraIndex < cameras.size(); cameraIndex++)
            {
                CameraWrapper cam   = cameras.get(cameraIndex);
                BufferedImage image = imageSet.get(cameraIndex);

                // only reset the first time (so we assert every other time)
                if (cam.width == -1 || cam.height == -1) {
                    cam.width = image.getWidth();
                    cam.height = image.getHeight();
                }

                assert(cam.width == image.getWidth());
                assert(cam.height == image.getHeight());
            }

            // create a wrapper for the image data
            MosaicWrapper mosaic = new MosaicWrapper();
            mosaic.imageSet = imageSet;
            mosaic.detectionSet = detectionSet;
            mosaic.MosaicToCameraXyzrpys = new HashMap<Integer,double[]>();

            mosaics.add(mosaic);
        }

        // Update all of the system state. The order below (intrinsics,
        // mosaics, camera extrinsics) naturally forms a topological ordering,
        // which means that we can update them one-by-one and any changes that
        // would affect the camera extrinsics, for example, would already have
        // been made in the previous two calls
        updateIntrinsics();
        updateMosaicExtrinsics();
        updateSubsystemExtrinsics();
    }

    /** Estimate camera intrinsics.
      *
      * Depends on the number of images in the system
      */
    void updateIntrinsics()
    {

    }

    /** Estimate mosaic-to-camera extrinsics.
      *
      * Depends on camera intrinsics
      */
    void updateMosaicExtrinsics()
    {

    }

    /** Try to perform a merge if any mosaics define rigid body transformations
      * between disconnected subsystems.
      *
      * Depends on camera intrinsics and mosaic extrinsics
      */
    void updateSubsystemExtrinsics()
    {

    }
}

