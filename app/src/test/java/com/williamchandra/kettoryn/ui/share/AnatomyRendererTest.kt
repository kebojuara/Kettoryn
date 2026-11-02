package com.williamchandra.kettoryn.ui.share

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.williamchandra.kettoryn.data.local.*
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnatomyRendererTest {
    private fun detail(muscle: String, type: SetType=SetType.WORK, complete: Boolean=true): SessionWithExercises = SessionWithExercises(
        WorkoutSessionEntity(id=1,title="Upper body",startedAt=0,endedAt=600000,updatedAt=600000,status=SessionStatus.COMPLETED),
        listOf(SessionExerciseWithSets(SessionExerciseEntity(id=1,sessionId=1,exerciseId=null,nameSnapshot="Bench",muscleSnapshot=muscle,equipmentSnapshot="Private",sortOrder=0),
            listOf(LoggedSetEntity(id=1,sessionExerciseId=1,sortOrder=0,reps=8,weightGrams=10000,isComplete=complete,setType=type)))))
    @Test fun primaryMuscleContractCoversRequiredGroupsAndKeepsUnknownNeutral() {
        val sourceGroups=AnatomyVectorData.views.flatMap { it.muscles }.map { it.group }.toSet()
        for(muscle in listOf("Chest","Lats","Back","Rear Delts","Biceps","Triceps","Quads","Hamstrings","Calves","Glutes","Core","Forearms")) {
            val mapped=AnatomyMuscleMap.regions(muscle)
            assertTrue("Mapped $muscle",mapped.isNotEmpty());assertTrue(sourceGroups.containsAll(mapped))
        }
        assertEquals(setOf("SHOULDERS_REAR"),AnatomyMuscleMap.regions("Rear Delts"))
        assertEquals(setOf("LATS"),AnatomyMuscleMap.regions("Lats"))
        assertTrue(AnatomyMuscleMap.regions("Full Body").isEmpty());assertTrue(AnatomyMuscleMap.regions("Legs").isEmpty())
        assertEquals(1,AnatomyMuscleMap.fromSession(detail("Unknown")).unmappedSets)
        assertTrue(AnatomyMuscleMap.fromSession(detail("Chest",SetType.WARMUP)).groups.isEmpty())
        assertTrue(AnatomyMuscleMap.fromSession(detail("Chest",complete=false)).groups.isEmpty())
        assertEquals(setOf("CHEST"),AnatomyMuscleMap.fromSession(detail("Chest")).groups)
    }
    @Test fun chestHighlightChangesOnlyAnatomyPixelsAndPrivacyRemovesThoseDifferences() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val trained=WorkoutShareRenderer.renderShareCardBitmap(detail("Chest"),WeightUnit.KG,context=context)
        val neutral=WorkoutShareRenderer.renderShareCardBitmap(detail("Unknown"),WeightUnit.KG,context=context)
        var changed=0
        for(y in 0 until trained.height step 2) for(x in 0 until trained.width step 2) {
            if(trained.getPixel(x,y)!=neutral.getPixel(x,y)) {
                changed++;assertTrue("Change belongs to anatomical column",x in 756..1152 && y in 170..662)
            }
        }
        assertTrue("A real segmented region is highlighted",changed>500)
        val options=WorkoutShareOptions(showMuscleMap=false)
        assertTrue(WorkoutShareRenderer.renderShareCardBitmap(detail("Chest"),WeightUnit.KG,options,context).sameAs(
            WorkoutShareRenderer.renderShareCardBitmap(detail("Unknown"),WeightUnit.KG,options,context)))
    }
}
