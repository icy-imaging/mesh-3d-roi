/*
 * Copyright (c) 2010-2026. Institut Pasteur.
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

package plugins.adufour.roi.mesh.polyhedron;

import plugins.adufour.roi.mesh.Cell3D;
import vtk.vtkCell3D;

/**
 * Generic structural element of a {@link ROI3DPolyhedralMesh}
 *
 * @author Alexandre Dufour
 * @see Tetrahedron3D
 */
public abstract class Polyhedron3D extends Cell3D {
    /**
     * Creates a new polygon from the specified vertex indices.
     * This constructor is protected as it should not be used directly by client code.
     * Client code should use
     * {@link ROI3DPolyhedralMesh#createCell(vtk.CellType, int...)} instead
     *
     * @param vertexIndices the vertex indices, in an order allowed by the local
     *                      convention
     */
    protected Polyhedron3D(final int... vertexIndices) {
        super(vertexIndices);
    }

    /**
     * @return a VTK representation of this polyhedron
     */
    public abstract vtkCell3D createVTKCell();
}
