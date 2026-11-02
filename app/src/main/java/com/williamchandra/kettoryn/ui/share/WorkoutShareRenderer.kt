package com.williamchandra.kettoryn.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.williamchandra.kettoryn.data.local.SessionWithExercises
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WorkoutShareRenderer {
    const val CARD_WIDTH = 1200
    const val CARD_HEIGHT = 800
    const val STORY_WIDTH = 1080
    const val STORY_HEIGHT = 1920

    fun renderShareCardBitmap(
        detail: SessionWithExercises,
        weightUnit: WeightUnit,
        options: WorkoutShareOptions = WorkoutShareOptions(),
        context: Context? = null
    ): Bitmap {
        val content = WorkoutShareContent.from(detail, weightUnit, options)
        val dark = options.darkCardStyle
        val foreground = if (dark) 0xFFEFF2F7.toInt() else 0xFF17202B.toInt()
        val secondary = if (dark) 0xFFB0BAC8.toInt() else 0xFF5D6875.toInt()
        val accent = if (dark) 0xFF9EB6D8.toInt() else 0xFF3E648F.toInt()
        val surface = if (dark) 0xFF0D1015.toInt() else 0xFFF7F7F4.toInt()
        val divider = if (dark) 0xFF2A333F.toInt() else 0xFFD4D9DF.toInt()
        fun face(weight: Int): Typeface {
            val resource = when {
                weight >= 700 -> com.williamchandra.kettoryn.R.font.manrope_bold
                weight >= 600 -> com.williamchandra.kettoryn.R.font.manrope_semibold
                weight >= 500 -> com.williamchandra.kettoryn.R.font.manrope_medium
                else -> com.williamchandra.kettoryn.R.font.manrope_regular
            }
            return context?.resources?.getFont(resource) ?: Typeface.create(if (weight >= 600) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        }
        fun layout(value: String, size: Float, color: Int, weight: Int, width: Float, lines: Int = 1): android.text.StaticLayout {
            val paint=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color;textSize=size;typeface=face(weight);fontFeatureSettings="tnum" }
            return android.text.StaticLayout.Builder.obtain(value,0,value.length,paint,width.toInt())
                .setMaxLines(lines).setEllipsize(android.text.TextUtils.TruncateAt.END).setIncludePad(false).setLineSpacing(0f,1.05f).build()
        }
        val leftWidth=if(options.showMuscleMap) 660f else 1072f
        val title=layout(content.title,88f,foreground,600,leftWidth,2)
        var exerciseNames=if(options.showExerciseList) content.exercises.map { layout(it.name,52f,foreground,500,leftWidth-108,3) } else emptyList()
        var rowHeights=exerciseNames.map { maxOf(128f,it.height+24f) }
        val listStart=maxOf(240f,56f+title.height+44f)
        var remaining=content.remainingExercises
        while(listStart+rowHeights.sum()+(if(remaining>0)76 else 0)+128>1280 && exerciseNames.size>1) {
            exerciseNames=exerciseNames.dropLast(1);rowHeights=rowHeights.dropLast(1);remaining++
        }
        val visibleExercises=content.exercises.take(exerciseNames.size)
        val height=if(options.template==ShareTemplate.SUMMARY) CARD_HEIGHT else
            maxOf(950,(listStart+rowHeights.sum()+(if(remaining>0)76 else 0)+128).toInt()).coerceAtMost(1280)
        val bitmap=Bitmap.createBitmap(CARD_WIDTH,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=surface }
        canvas.drawRoundRect(RectF(1f,1f,CARD_WIDTH-1f,height-1f),32f,32f,paint)
        paint.color=divider;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
        canvas.drawRoundRect(RectF(1f,1f,CARD_WIDTH-1f,height-1f),32f,32f,paint);paint.style=Paint.Style.FILL
        fun draw(layout: android.text.StaticLayout,x: Float,top: Float) { val save=canvas.save();canvas.translate(x,top);layout.draw(canvas);canvas.restoreToCount(save) }
        fun text(value: String,x: Float,top: Float,size: Float,color: Int,width: Float=leftWidth,weight: Int=400) = draw(layout(value,size,color,weight,width),x,top)
        fun number(value: String,x: Float,top: Float,maxSize: Float,width: Float,color: Int=foreground) {
            val measure=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=maxSize;typeface=face(600);fontFeatureSettings="tnum" }
            val fitted=minOf(maxSize,maxSize*(width-4)/measure.measureText(value).coerceAtLeast(1f))
            draw(layout(value,fitted,color,600,width),x,top)
        }
        draw(title,64f,56f)
        if(options.template==ShareTemplate.SUMMARY) {
            paint.color=divider;canvas.drawRect(64f,270f,64f+leftWidth,272f,paint)
            number(if(options.showWeightsAndVolume) content.volume else content.completedSets.toString(),64f,306f,104f,leftWidth)
            text(if(options.showWeightsAndVolume) "Volume" else "Completed sets",64f,432f,44f,accent,weight=500)
            val supporting=buildList {
                if(detail.session.endedAt!=null) add("Duration" to content.duration)
                if(options.showWeightsAndVolume) add("Completed sets" to content.completedSets.toString())
            }
            supporting.forEachIndexed { index,(label,value) ->
                val width=leftWidth/2-16;val x=64f+index*(leftWidth/2+8)
                number(value,x,546f,68f,width);text(label,x,628f,40f,secondary,width)
            }
        } else if(options.showExerciseList) {
            var y=listStart
            if(content.exercises.isEmpty()) text("No completed work sets",64f,y+80,44f,secondary)
            visibleExercises.forEachIndexed { index,exercise ->
                number("${exercise.completedSets}×",64f,y+4,50f,88f,accent)
                draw(exerciseNames[index],172f,y)
                y+=rowHeights[index]
            }
            if(remaining>0) text("+ $remaining more",172f,y+8,42f,secondary,leftWidth-108)
        } else {
            number(content.completedSets.toString(),64f,330f,104f,leftWidth)
            text("Completed sets",64f,458f,44f,accent)
        }
        if(options.showMuscleMap) {
            val training=AnatomyMuscleMap.fromSession(detail)
            text("Muscles trained",776f,100f,40f,secondary,360f,500)
            AnatomyRenderer.draw(canvas,RectF(756f,170f,1152f,height-138f),training.groups,dark,face(400))
        }
        paint.color=divider;canvas.drawRect(64f,height-104f,1136f,height-102f,paint)
        drawApprovedKLogoOnCanvas(canvas,64f,height-76f,58f,foreground,accent)
        text("Kettoryn",144f,height-80f,40f,secondary,340f,500)
        return bitmap
    }

    internal fun fittedValueSize(value: String, maxWidth: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = 44f
        }
        return (44f * (maxWidth - 8f) / paint.measureText(value).coerceAtLeast(1f)).coerceAtMost(44f)
    }

    // Actual card pixels are centered in the Story's safe area on a full neutral canvas.
    fun renderStoryBitmap(card: Bitmap, darkCard: Boolean): Bitmap {
        val story=Bitmap.createBitmap(STORY_WIDTH,STORY_HEIGHT,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(story)
        canvas.drawColor(if(darkCard)0xFF171B22.toInt() else 0xFFD5D7D9.toInt())
        val width=984f
        val height=width*card.height/card.width
        val top=220f+(STORY_HEIGHT-220f-270f-height)/2
        val bounds=RectF(48f,top,1032f,top+height)
        val shadow=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=if(darkCard)0xFF0D1015.toInt() else 0xFFF7F7F4.toInt()
            setShadowLayer(24f,0f,12f,if(darkCard)0x60000000 else 0x28000000)
        }
        canvas.drawRoundRect(bounds,28f,28f,shadow)
        canvas.drawBitmap(card,null,bounds,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return story
    }

    /**
     * Draws the exact 6-piece Approved K brand mark onto an Android [Canvas].
     * Coordinates follow the original Kettoryn brand mark in its 738 x 506 view box.
     */
    fun drawApprovedKLogoOnCanvas(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        structuralColor: Int,
        limeColor: Int
    ) {
        val scale = width / 738f
        fun tx(x: Float): Float = left + x * scale
        fun ty(y: Float): Float = top + y * scale
        fun tr(r: Float): Float = r * scale

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = structuralColor
        }

        fun drawLeftRoundedBar(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float) {
            val X1 = tx(x1)
            val Y1 = ty(y1)
            val X2 = tx(x2)
            val Y2 = ty(y2)
            val R = tr(radius)
            val p = Path().apply {
                moveTo(X1 + R, Y1)
                lineTo(X2, Y1)
                lineTo(X2, Y2)
                lineTo(X1 + R, Y2)
                quadTo(X1, Y2, X1, Y2 - R)
                lineTo(X1, Y1 + R)
                quadTo(X1, Y1, X1 + R, Y1)
                close()
            }
            canvas.drawPath(p, paint)
        }

        // 3 ascending left-rounded weight bars
        drawLeftRoundedBar(0f, 219f, 37f, 328f, 14f)
        drawLeftRoundedBar(51f, 154f, 97f, 394f, 18f)
        drawLeftRoundedBar(115f, 85f, 192f, 458f, 24f)

        // Upper spine
        val upperSpine = Path().apply {
            moveTo(tx(221f), ty(0f))
            lineTo(tx(344f), ty(0f))
            quadTo(tx(372f), ty(0f), tx(372f), ty(28f))
            lineTo(tx(372f), ty(139f))
            lineTo(tx(221f), ty(290f))
            close()
        }
        canvas.drawPath(upperSpine, paint)

        // Lower spine
        val lowerSpine = Path().apply {
            moveTo(tx(221f), ty(306f))
            lineTo(tx(372f), ty(457f))
            lineTo(tx(372f), ty(477f))
            quadTo(tx(372f), ty(505f), tx(344f), ty(505f))
            lineTo(tx(221f), ty(505f))
            close()
        }
        canvas.drawPath(lowerSpine, paint)

        // Lower diagonal leg
        val lowerLeg = Path().apply {
            moveTo(tx(271f), ty(289f))
            lineTo(tx(458f), ty(289f))
            lineTo(tx(705f), ty(505f))
            lineTo(tx(474f), ty(505f))
            lineTo(tx(259f), ty(300f))
            close()
        }
        canvas.drawPath(lowerLeg, paint)

        // Upper diagonal lime arm
        paint.color = limeColor
        val limeArm = Path().apply {
            moveTo(tx(277f), ty(284f))
            lineTo(tx(551f), ty(10f))
            lineTo(tx(737f), ty(10f))
            lineTo(tx(461f), ty(284f))
            close()
        }
        canvas.drawPath(limeArm, paint)
    }

    fun saveBitmapToCacheFile(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "shares")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir.listFiles()?.filter { it.name.startsWith("kettoryn-workout-") && System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val file = File.createTempFile("kettoryn-workout-", ".png", dir)
        FileOutputStream(file).use { fos ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) { "Could not encode workout card." }
            fos.flush()
        }
        return file
    }

    fun writeBitmapToOutputStream(bitmap: Bitmap, outputStream: OutputStream) {
        outputStream.use { os ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) { "Could not encode workout card." }
            os.flush()
        }
    }

    fun getShareContentUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun shareViaSystemSheet(context: Context, bitmap: Bitmap, workoutTitle: String) {
        val file = withContext(Dispatchers.IO) { saveBitmapToCacheFile(context, bitmap) }
        val uri = getShareContentUri(context, file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, workoutTitle)
            clipData = android.content.ClipData.newRawUri("Workout card", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Share workout").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Attempts to launch Instagram Story directly (`com.instagram.share.ADD_TO_STORY`).
     * If Instagram is not installed or fails to resolve, automatically falls back to the
     * Android system Sharesheet and returns false.
     */
    suspend fun shareToInstagramStoryOrFallback(
        context: Context,
        bitmap: Bitmap,
        workoutTitle: String,
        darkCard: Boolean
    ): Boolean {
        val story=withContext(Dispatchers.Default) { renderStoryBitmap(bitmap,darkCard) }
        try {
            val target=Intent("com.instagram.share.ADD_TO_STORY").apply { setPackage("com.instagram.android");type="image/png" }
            if(context.packageManager.resolveActivity(target,0)==null) {
                shareViaSystemSheet(context,story,workoutTitle)
                return false
            }
            val file=withContext(Dispatchers.IO) { saveBitmapToCacheFile(context,story) }
            val uri=getShareContentUri(context,file)
            val background=if(darkCard) "#171B22" else "#D5D7D9"
            val intent=Intent("com.instagram.share.ADD_TO_STORY").apply {
                setPackage("com.instagram.android");setDataAndType(uri,"image/png")
                clipData=android.content.ClipData.newRawUri("Workout story",uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("top_background_color",background);putExtra("bottom_background_color",background)
                putExtra("source_application",context.packageName)
            }
            return try {
                context.grantUriPermission("com.instagram.android",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
                true
            } catch (_: Exception) { shareViaSystemSheet(context,story,workoutTitle);false }
        } finally { story.recycle() }
    }
}
