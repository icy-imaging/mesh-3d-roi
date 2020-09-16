package plugins.adufour.roi.mesh.polyhedron;

import vtk.vtkCell3D;
import vtk.vtkWedge;

/**
 * Specialized implementation of a polyhedron in the form of a wedge (2 parallel
 * polygons with equal number of points).
 * <p>
 * Vertex ordering convention: (A1, ..., An, B1, ..., Bn)
 * <ul>
 * <li>Each half of the index buffer describes one face of the wedge</li>
 * <li>Vertex indices from different faces are pair-wise aligned</li>
 * <li>The front face is listed in counter-clockwise order</li>
 * </ul>
 * 
 * @author Alexandre Dufour
 */
public class Wedge3D extends Polyhedron3D
{
    public Wedge3D(final int... vertexIndices)
    {
        super(vertexIndices);

        if (vertexIndices.length % 2 != 0)
            throw new IllegalArgumentException("A wedge is defined by an even number of vertices");
    }

    @Override
    public Wedge3D clone()
    {
        return new Wedge3D(vertexIndices);
    }

    @Override
    public vtkCell3D createVTKCell()
    {
        return new vtkWedge();
    }
}
