package plugins.adufour.roi.mesh.polygon;

import java.util.List;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import plugins.adufour.roi.mesh.Cell3D;
import plugins.adufour.roi.mesh.Vertex3D;


/**
 * Structural element of a {@link ROI3DPolygonalMesh 3D polygonal mesh}, representing a flat polygon
 * in 3D space. A polygon does not store the vertex locations <i>per se</i>, but rather stores their
 * indices in the vertex buffer of the corresponding mesh. By convention, polygon vertices are
 * stored in counter-clockwise order when looking at the front of the face
 * 
 * @author Alexandre Dufour
 */
public class Polygon3D extends Cell3D
{
    /**
     * Creates a new polygon from the specified vertex indices. This constructor is protected as it
     * should not be used directly by client code. Client code should use
     * {@link ROI3DPolygonalMesh#createCell(int[])} instead
     * 
     * @param vertexIndices
     *            the vertex indices, in counter-clockwise order
     */
    protected Polygon3D(int... vertexIndices)
    {
        super(vertexIndices);
    }
    
    @Override
    public Polygon3D clone()
    {
        return new Polygon3D(this.vertexIndices);
    }
    
    /**
     * Indicates whether the edge formed by the specified vertex indices belongs to this face
     * 
     * @param vertexIndex1
     *            the index of the edge's first vertex
     * @param vertexIndex2
     *            the index of the edge's second vertex
     * @param inThatOrder
     *            <code>true</code> if the two indices must be found in the specified order
     *            (suggesting counter-clockwise ordering by convention), or <code>false</code> if
     *            the order is unimportant
     * @return
     */
    public boolean containsEdge(int vertexIndex1, int vertexIndex2, boolean inThatOrder)
    {
        int i1 = indexOf(vertexIndex1);
        int i2 = indexOf(vertexIndex2);
        
        if (i1 == -1 || i2 == -1) return false;
        
        return !inThatOrder || ((i1 + 1) % size == i2);
    }
    
    /**
     * Calculate the surface of this polygon, using the specified vertex list to fetch the vertex
     * positions
     * 
     * @param vertices
     *            the vertex list where to fetch the positions from
     * @return the surface of this polygon
     */
    public double getArea(List<Vertex3D> vertices)
    {
        Vector3d v12 = new Vector3d();
        Vector3d v13 = new Vector3d();
        Vector3d cross = new Vector3d();
        
        // if the face has more than 3 vertices,
        // split it into a triangle fan
        // the first vertex of the fan never changes
        // => has to be a vector for the final cross product
        
        Vector3d v1 = new Vector3d(vertices.get(vertexIndices[0]).position);
        
        double surface = 0;
        
        for (int i = 1; i < size - 1; i++)
        {
            Point3d v2 = vertices.get(vertexIndices[i]).position;
            Point3d v3 = vertices.get(vertexIndices[i + 1]).position;
            
            v12.sub(v2, v1);
            v13.sub(v3, v1);
            
            cross.cross(v12, v13);
            
            double surf = cross.length() * 0.5f;
            
            surface += surf;
        }
        
        return surface;
    }
    
    /**
     * Indicates whether the specified edge follows the ordering convention of this cell (this is
     * implementation dependent)
     * 
     * @param v1
     *            the first vertex index
     * @param v2
     *            the second vertex index
     * @return <code>true</code> if <code>v1</code> and <code>v2</code> are ordered following the
     *         convention in this cell (counter-clockwise in the present case), and
     *         <code>false</code> otherwise
     * @throws IllegalArgumentException
     *             if one of the given indices does not belong to this cell
     */
    public boolean isEdgeOrdered(int v1, int v2) throws IllegalArgumentException
    {
        int i1 = indexOf(v1);
        
        if (i1 == -1) throw new IllegalArgumentException("Vertex index " + i1 + " does not belong to this face");
        
        int i2 = indexOf(v2);
        
        if (i2 == -1) throw new IllegalArgumentException("Vertex index " + i2 + " does not belong to this face");
        
        return (i1 + 1) % size == i2;
    }
}
