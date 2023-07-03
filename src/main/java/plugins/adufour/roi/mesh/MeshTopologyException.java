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

import plugins.adufour.roi.mesh.polygon.ROI3DTriangularMesh;

/**
 * Exception that occurs when the topology of a {@link ROI3DMesh} is
 * inconsistent. A typical example is when a {@link ROI3DTriangularMesh} is
 * splitting as a result of a resampling operation, giving rise to two new
 * meshes.
 *
 * @author Alexandre Dufour
 * @see ROI3DTriangularMesh#resample(double, double)
 */
public class MeshTopologyException extends Exception {
    private static final long serialVersionUID = 1L;

    public final ROI3DMesh<?> source;

    public final ROI3DMesh<?>[] children;

    /**
     * Creates a new Topology exception for the specified contour
     *
     * @param contour  the contour undergoing a topology break
     * @param children an array containing zero or more contours that should replace the contour raising
     *                 the exception (typically when a mesh vanishes or splits as a result of a
     *                 resampling operation)
     */
    public <C extends Cell3D> MeshTopologyException(final ROI3DMesh<C> contour, final ROI3DMesh<C>[] children) {
        super("Topology break detected in contour " + contour.hashCode());
        this.source = contour;
        this.children = children;
    }
}
