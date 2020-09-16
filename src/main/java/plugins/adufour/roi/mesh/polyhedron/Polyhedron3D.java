package plugins.adufour.roi.mesh.polyhedron;

import plugins.adufour.roi.mesh.Cell3D;
import vtk.vtkCell3D;

/**
 * Generic structural element of a {@link ROI3DPolyhedralMesh}
 * 
 * @see Tetrahedron3D
 * @author Alexandre Dufour
 */
public abstract class Polyhedron3D extends Cell3D
{
    /**
	 * Creates a new polygon from the specified vertex indices. This constructor
	 * is protected as it should not be used directly by client code. Client
	 * code should use
	 * {@link ROI3DPolyhedralMesh#createCell(vtk.CellType, int...)} instead
	 * 
	 * @param vertexIndices
	 *            the vertex indices, in an order allowed by the local
	 *            convention
	 */
    protected Polyhedron3D(final int... vertexIndices)
    {
        super(vertexIndices);
    }
    
    /**
     * @return a VTK representation of this polyhedron
     */
    public abstract vtkCell3D createVTKCell();   
}
