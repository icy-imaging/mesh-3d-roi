package plugins.adufour.roi.mesh.polyhedron;

import vtk.vtkCell3D;
import vtk.vtkPyramid;

/**
 * Specialized implementation of a polyhedron in the form of a quad-based pyramid. By convention, if
 * vertex indices (0,1,2,3) describe the (coplanar) base of the pyramid, then vertex index (4) is
 * located in the direction of the base's normal vector.
 * 
 * @author Alexandre Dufour
 */
public class Pyramid3D extends Polyhedron3D
{
    public Pyramid3D(int... vertexIndices)
    {
        super(vertexIndices);
    }

    @Override
    public Pyramid3D clone()
    {
        return new Pyramid3D(vertexIndices);
    }

    @Override
    public vtkCell3D createVTKCell()
    {
        return new vtkPyramid();
    }
}
