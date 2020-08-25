package plugins.adufour.roi.mesh;

import plugins.adufour.roi.mesh.polygon.Polygon3D;
import plugins.adufour.roi.mesh.polyhedron.Polyhedron3D;

/**
 * Structural element of a generic {@link ROI3DMesh 3D mesh}.
 * 
 * @see Polygon3D
 * @see Polyhedron3D
 * @author Alexandre Dufour
 */
public abstract class Cell3D
{
    /**
     * The indices of the vertex forming this cell, in arbitrary order (it is up to the overriding
     * classes to define how vertices should be ordered)
     */
    public final int[] vertexIndices;

    /**
     * The number of vertices in this face (point: 1, edge: 2, triangle: 3, quad: 4, etc.)
     */
    public final int size;

    /**
     * Creates a new mesh cell from the specified vertex indices
     * 
     * @param vertexIndices
     */
    protected Cell3D(int... vertexIndices)
    {
        this.vertexIndices = new int[vertexIndices.length];
        System.arraycopy(vertexIndices, 0, this.vertexIndices, 0, vertexIndices.length);
        size = vertexIndices.length;
    }

    /**
     * Creates a copy of this cell.
     */
    public abstract Cell3D clone();

    /**
     * Indicates whether the specified vertex index belongs to this face
     * 
     * @param index
     *        the vertex index to look for
     * @return <code>true</code> if the index is present in this face, <code>false</code> otherwise
     */
    public boolean contains(int index)
    {
        for (int i : vertexIndices)
            if (i == index)
                return true;

        return false;
    }

    /**
     * @param vertexIndex
     * @return a zero-based index where the specified vertex index has been found, or
     *         <code>-1</code> if the specified vertex index was not found
     */
    public int indexOf(int vertexIndex)
    {
        for (int i = 0; i < size; i++)
            if (vertexIndices[i] == vertexIndex)
                return i;

        return -1;
    }

    /**
     * Replaces a vertex index by another (or simply returns if the index to replace is not found in
     * this cell)
     * 
     * @param oldIndex
     *        the index of the old vertex
     * @param newIndex
     *        the index of the new vertex
     */
    public void replace(int oldIndex, int newIndex)
    {
        int position = indexOf(oldIndex);

        if (position >= 0)
            vertexIndices[position] = newIndex;
    }

    /**
     * Replaces the vertex indices with the specified indexes
     * 
     * @param vertexIndices
     * @throws IllegalArgumentException
     *         if the size of the specified list is different from the size of this cell
     */
    public void setIndices(int... vertexIndices) throws IllegalArgumentException
    {
        if (vertexIndices.length != size)
            throw new IllegalArgumentException("Invalid cell size: " + vertexIndices.length);

        System.arraycopy(vertexIndices, 0, this.vertexIndices, 0, size);
    }
}
