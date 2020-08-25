package plugins.adufour.roi.mesh;

import plugins.adufour.roi.mesh.polygon.ROI3DTriangularMesh;


/**
 * Exception that occurs when the topology of a {@link ROI3DMesh} is inconsistent. A typical example
 * is when a {@link ROI3DTriangularMesh} is splitting as a result of a resampling operation, giving
 * rise to two new meshes.
 * 
 * @see ROI3DTriangularMesh#reSampleToAverageDistance(double, double)
 * @author Alexandre Dufour
 */
public class MeshTopologyException extends Exception
{
    private static final long   serialVersionUID = 1L;
    
    public final ROI3DMesh<?>   source;
    
    public final ROI3DMesh<?>[] children;
    
    /**
     * Creates a new Topology exception for the specified contour
     * 
     * @param contour
     *            the contour undergoing a topology break
     * @param children
     *            an array containing zero or more contours that should replace the contour raising
     *            the exception (typically when a mesh vanishes or splits as a result of a
     *            resampling operation)
     */
    public <C extends Cell3D> MeshTopologyException(ROI3DMesh<C> contour, ROI3DMesh<C>[] children)
    {
        super("Topology break detected in contour " + contour.hashCode());
        this.source = contour;
        this.children = children;
    }
}
