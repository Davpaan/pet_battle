package com.petbattle.features.defense.domain.entities

import kotlin.math.floor
import kotlin.math.hypot

/** Un punto en píxeles de mapa (origen en la esquina superior izquierda de la imagen del mapa). */
data class MapPoint(val x: Double, val y: Double) {
    fun distanceTo(other: MapPoint) = hypot(x - other.x, y - other.y)
}

/** Polilínea que siguen los enemigos, desde la aparición (normalmente fuera del mapa) hasta la salida. */
data class MapRoute(val name: String, val points: List<MapPoint>)

/**
 * Descripción estática de un nivel: tamaño, rutas transitables y dónde pueden
 * colocarse las mascotas. Solo datos; los mapas generados viven en `maps/`.
 */
class DefenseMapEntity(
    val width: Int,
    val height: Int,
    val cellSize: Int,
    val routes: List<MapRoute>,
    /** Una cadena por fila de celdas: `#` = hierba (colocable), cualquier otra cosa no. */
    val placementGrid: List<String>,
) {
    val columns get() = placementGrid.first().length
    val rows get() = placementGrid.size

    fun isGrassCell(col: Int, row: Int) =
        row in 0 until rows && col in 0 until columns && placementGrid[row][col] == '#'

    /** Si un círculo de huella de radio [radius] centrado en [p] está todo sobre hierba. */
    fun canPlace(p: MapPoint, radius: Double): Boolean {
        val c0 = floor((p.x - radius) / cellSize).toInt()
        val c1 = floor((p.x + radius) / cellSize).toInt()
        val r0 = floor((p.y - radius) / cellSize).toInt()
        val r1 = floor((p.y + radius) / cellSize).toInt()
        for (r in r0..r1) for (c in c0..c1) {
            // El punto de la celda más cercano al centro decide si se toca.
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
