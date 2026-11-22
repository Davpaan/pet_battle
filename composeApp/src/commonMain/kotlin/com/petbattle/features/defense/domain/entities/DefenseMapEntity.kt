package com.petbattle.features.defense.domain.entities

import kotlin.math.floor
import kotlin.math.hypot

/** A point in map pixels (origin top-left of the map image). */
data class MapPoint(val x: Double, val y: Double) {
    fun distanceTo(other: MapPoint) = hypot(x - other.x, y - other.y)
}

/** Polyline enemies follow, from spawn (usually off-map) to the exit. */
data class MapRoute(val name: String, val points: List<MapPoint>)

/**
 * Static description of a level: size, walkable routes and where pets may
 * stand. Data only; generated maps live in `maps/`.
 */
class DefenseMapEntity(
    val width: Int,
    val height: Int,
    val cellSize: Int,
    val routes: List<MapRoute>,
    /** One string per row of cells: `#` = grass (placeable), anything else not. */
    val placementGrid: List<String>,
) {
    val columns get() = placementGrid.first().length
    val rows get() = placementGrid.size

    fun isGrassCell(col: Int, row: Int) =
        row in 0 until rows && col in 0 until columns && placementGrid[row][col] == '#'

    /** Whether a footprint circle of [radius] centred on [p] is all grass. */
    fun canPlace(p: MapPoint, radius: Double): Boolean {
        val c0 = floor((p.x - radius) / cellSize).toInt()
        val c1 = floor((p.x + radius) / cellSize).toInt()
        val r0 = floor((p.y - radius) / cellSize).toInt()
        val r1 = floor((p.y + radius) / cellSize).toInt()
        for (r in r0..r1) for (c in c0..c1) {
            // Closest point of the cell to the centre decides if it's touched.
            val nx = p.x.coerceIn(c * cellSize.toDouble(), (c + 1) * cellSize - 1e-6)
            val ny = p.y.coerceIn(r * cellSize.toDouble(), (r + 1) * cellSize - 1e-6)
            val dx = nx - p.x
            val dy = ny - p.y
            if (dx * dx + dy * dy > radius * radius) continue
            if (!isGrassCell(c, r)) return false
        }
        return true
    }
}
