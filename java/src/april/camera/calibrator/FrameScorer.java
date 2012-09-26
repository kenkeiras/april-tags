package april.camera.calibrator;

import java.awt.image.*;
import java.util.*;
import april.tag.*;

public interface FrameScorer
{

    public double scoreFrame(BufferedImage im, List<TagDetection> dets);

}