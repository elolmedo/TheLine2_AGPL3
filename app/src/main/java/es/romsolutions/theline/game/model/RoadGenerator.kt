package es.romsolutions.theline.game.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.sin

class RoadGenerator {
    /**
     * Generates a road segment.
     * @param startY The starting Y coordinate (bottom of the segment)
     * @param endY The ending Y coordinate (top of the segment)
     * @param level The current level to determine road shape
     * @param screenWidth The width of the screen
     */
    fun generateRoad(startY: Float, endY: Float, level: Int, screenWidth: Float): List<Offset> {
        val points = mutableListOf<Offset>()
        val step = 10f
        val centerX = screenWidth / 2f
        
        var currentY = startY
        while (currentY >= endY) {
            val x = when {
                level <= 5 -> centerX // Straight line
                level <= 10 -> { // S-curves
                    centerX + (screenWidth * 0.3f) * sin(currentY * 0.005f)
                }
                else -> { // Mixed (S-curves + straight)
                    // Every 500 pixels swap between S and straight
                    val phase = (currentY / 500f).toInt()
                    if (phase % 2 == 0) {
                        centerX + (screenWidth * 0.3f) * sin(currentY * 0.005f)
                    } else {
                        centerX
                    }
                }
            }
            points.add(Offset(x, currentY))
            currentY -= step
        }
        return points
    }
}
