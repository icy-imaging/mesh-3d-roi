/*
 * Copyright (c) 2010-2023. Institut Pasteur.
 *
 * This file is part of Icy.
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <https://www.gnu.org/licenses/>.
 */

package plugins.adufour.roi.mesh;

import plugins.adufour.roi.mesh.polygon.Polygon3D;
import plugins.adufour.roi.mesh.polyhedron.Polyhedron3D;

/**
 * Structural element of a generic {@link ROI3DMesh 3D mesh}.
 *
 * @author Alexandre Dufour
 * @see Polygon3D
 * @see Polyhedron3D
 */
public abstract class Cell3D {
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
     */
    protected Cell3D(final int... vertexIndices) {
        this.vertexIndices = new int[vertexIndices.length];
        System.arraycopy(vertexIndices, 0, this.vertexIndices, 0, vertexIndices.length);
        size = vertexIndices.length;
    }

    /**
     * Creates a copy of this cell.
     */
    @Override
    public abstract Cell3D clone();

    /**
     * Indicates whether the specified vertex index belongs to this face
     *
     * @param index the vertex index to look for
     * @return <code>true</code> if the index is present in this face, <code>false</code> otherwise
     */
    public boolean contains(final int index) {
        for (final int i : vertexIndices)
            if (i == index)
                return true;

        return false;
    }

    /**
     * @return a zero-based index where the specified vertex index has been found, or
     * <code>-1</code> if the specified vertex index was not found
     */
    public int indexOf(final int vertexIndex) {
        for (int i = 0; i < size; i++)
            if (vertexIndices[i] == vertexIndex)
                return i;

        return -1;
    }

    /**
     * Replaces a vertex index by another (or simply returns if the index to replace is not found in
     * this cell)
     *
     * @param oldIndex the index of the old vertex
     * @param newIndex the index of the new vertex
     */
    public void replace(final int oldIndex, final int newIndex) {
        final int position = indexOf(oldIndex);

        if (position >= 0)
            vertexIndices[position] = newIndex;
    }

    /**
     * Replaces the vertex indices with the specified indexes
     *
     * @throws IllegalArgumentException if the size of the specified list is different from the size of this cell
     */
    public void setIndices(final int... vertexIndices) throws IllegalArgumentException {
        if (vertexIndices.length != size)
            throw new IllegalArgumentException("Invalid cell size: " + vertexIndices.length);

        System.arraycopy(vertexIndices, 0, this.vertexIndices, 0, size);
    }
}
