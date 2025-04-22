package su.plo.voice.soundphysics;

import com.sonicether.soundphysics.SoundPhysicsMod;

public final class SoundPhysicsModConfig {

    public static boolean isEnabled() {
        return SoundPhysicsMod.CONFIG.enabled.get();
    }
}
