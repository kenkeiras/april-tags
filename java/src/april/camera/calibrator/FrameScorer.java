package april.camera.calibrator;

import java.util.*;
import april.tag.*;

public interface FrameScorer
{

    public double scoreFrame(List<TagDetection> dets);

}