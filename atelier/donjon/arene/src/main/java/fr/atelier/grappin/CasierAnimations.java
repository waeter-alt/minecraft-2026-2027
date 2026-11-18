// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/station/casier.bbmodel : ne pas modifier a la main.
package fr.atelier.grappin;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

public final class CasierAnimations {
    private CasierAnimations() {
    }

    public static final AnimationDefinition OUVRIR = AnimationDefinition.Builder.withLength(0.5F)
            .addAnimation("porte", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.3F, KeyframeAnimations.degreeVec(0.0F, 118.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.42F, KeyframeAnimations.degreeVec(0.0F, 106.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 110.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)))
            .build();

    public static final AnimationDefinition FERMER = AnimationDefinition.Builder.withLength(0.5F)
            .addAnimation("porte", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 110.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.32F, KeyframeAnimations.degreeVec(0.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.42F, KeyframeAnimations.degreeVec(0.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                        new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)))
            .build();
}
