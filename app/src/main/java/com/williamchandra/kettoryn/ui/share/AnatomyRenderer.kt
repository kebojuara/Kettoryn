package com.williamchandra.kettoryn.ui.share

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.PathParser
import com.williamchandra.kettoryn.data.local.SessionWithExercises
import com.williamchandra.kettoryn.domain.WorkoutMetricsCalculator
import java.util.Locale

data class AnatomicalTraining(val groups: Set<String>, val mappedSets: Int, val unmappedSets: Int)

/** Curated primary-group correspondence. Broad/unknown mobility and generic legs stay unmapped. */
object AnatomyMuscleMap {
    fun regions(primary: String): Set<String> = when (primary.trim().lowercase(Locale.ROOT)) {
        "chest", "pectorals" -> setOf("CHEST")
        "back" -> setOf("LATS", "RHOMBOIDS", "TRAPEZIUS", "BACK_LOWER")
        "lats", "latissimus dorsi" -> setOf("LATS")
        "upper back" -> setOf("RHOMBOIDS", "TRAPEZIUS")
        "lower back" -> setOf("BACK_LOWER")
        "shoulders", "deltoids" -> setOf("SHOULDERS_FRONT", "SHOULDERS_SIDE", "SHOULDERS_REAR")
        "rear delts", "rear deltoids" -> setOf("SHOULDERS_REAR")
        "biceps" -> setOf("BICEPS")
        "triceps" -> setOf("TRICEPS")
        "quads", "quadriceps" -> setOf("QUADS")
        "hamstrings" -> setOf("HAMSTRINGS")
        "calves" -> setOf("CALVES")
        "glutes", "gluteals" -> setOf("GLUTES")
        "core", "abs", "abdominals" -> setOf("CORE", "OBLIQUES")
        "obliques" -> setOf("OBLIQUES")
        "forearms" -> setOf("FOREARMS")
        "adductors" -> setOf("ADDUCTORS")
        "abductors" -> setOf("ABDUCTORS")
        "posterior chain" -> setOf("GLUTES", "HAMSTRINGS", "BACK_LOWER")
        else -> emptySet()
    }
    fun fromSession(detail: SessionWithExercises): AnatomicalTraining {
        val groups = linkedSetOf<String>();var mapped=0;var unknown=0
        detail.exercises.forEach { exercise ->
            val count=WorkoutMetricsCalculator.countCompletedWorkSetsFromEntities(exercise.sets)
            val regions=regions(exercise.sessionExercise.muscleSnapshot)
            if(regions.isEmpty()) unknown+=count else { mapped+=count;if(count>0) groups.addAll(regions) }
        }
        return AnatomicalTraining(groups,mapped,unknown)
    }
}

internal object AnatomyRenderer {
    private data class Region(val group: String,val path: android.graphics.Path)
    private data class View(val label: String,val outline: android.graphics.Path,val bounds: RectF,val regions: List<Region>)
    private val views by lazy {
        AnatomyVectorData.views.map { data ->
            val outline=checkNotNull(PathParser.createPathFromPathData(data.outline))
            val bounds=RectF();outline.computeBounds(bounds,true)
            View(data.label,outline,bounds,data.muscles.map { Region(it.group,checkNotNull(PathParser.createPathFromPathData(it.path))) })
        }
    }
    fun draw(canvas: Canvas, area: RectF, groups: Set<String>, dark: Boolean, typeface: android.graphics.Typeface) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val slot=area.width()/2
        views.forEachIndexed { index,view ->
            val box=RectF(area.left+index*slot+8,area.top,area.left+(index+1)*slot-8,area.bottom-56)
            val scale=minOf(box.width()/view.bounds.width(),box.height()/view.bounds.height())
            val saved=canvas.save()
            canvas.translate(box.centerX()-view.bounds.centerX()*scale,box.centerY()-view.bounds.centerY()*scale)
            canvas.scale(scale,scale)
            paint.style=Paint.Style.FILL;paint.color=if(dark)0xFF303A46.toInt() else 0xFFD7DCE1.toInt()
            canvas.drawPath(view.outline,paint)
            view.regions.forEach { region ->
                paint.style=Paint.Style.FILL
                paint.color=if(region.group in groups) { if(dark)0xFF9EB6D8.toInt() else 0xFF3E648F.toInt() }
                    else if(dark)0xFF66717D.toInt() else 0xFFB1BAC4.toInt()
                canvas.drawPath(region.path,paint)
                paint.style=Paint.Style.STROKE;paint.strokeWidth=3f
                paint.color=if(dark)0xFF303A46.toInt() else 0xFFF7F7F4.toInt();canvas.drawPath(region.path,paint)
            }
            canvas.restoreToCount(saved)
            paint.style=Paint.Style.FILL;paint.textSize=40f;paint.typeface=typeface
            paint.color=if(dark)0xFFABB5C3.toInt() else 0xFF5D6875.toInt();paint.textAlign=Paint.Align.CENTER
            canvas.drawText(view.label,area.left+(index+0.5f)*slot,minOf(area.bottom-4,box.centerY()+view.bounds.height()*scale/2+44),paint)
        }
    }
}
