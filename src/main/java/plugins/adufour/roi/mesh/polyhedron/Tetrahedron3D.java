package plugins.adufour.roi.mesh.polyhedron;

import vtk.vtkCell3D;
import vtk.vtkTetra;

/**
 * Specialized implementation of a polyhedron in the form of a tetrahedron. By convention, if vertex
 * indices (0,1,2) describe the base of the tetrahedron, then vertex index (3) is located in the
 * direction of the base's normal vector.
 * 
 * @author Alexandre Dufour
 */
public class Tetrahedron3D extends Polyhedron3D
{
    public Tetrahedron3D(int... vertexIndices)
    {
        super(vertexIndices);

        if (vertexIndices.length != 4)
            throw new IllegalArgumentException("A tetrahedron is defined by exactly 4 vertices");
    }

    @Override
    public Tetrahedron3D clone()
    {
        return new Tetrahedron3D(vertexIndices);
    }

    @Override
    public vtkCell3D createVTKCell()
    {
        return new vtkTetra();
    }
}
