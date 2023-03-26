package su.plo.voice.soundphysics;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import gg.essential.universal.wrappers.UPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import su.plo.config.entry.BooleanConfigEntry;
import su.plo.config.entry.DoubleConfigEntry;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.audio.device.AlAudioDevice;
import su.plo.voice.api.client.audio.device.DeviceException;
import su.plo.voice.api.client.audio.device.DeviceType;
import su.plo.voice.api.client.audio.device.OutputDevice;
import su.plo.voice.api.client.audio.device.source.AlSource;
import su.plo.voice.api.client.audio.source.LoopbackSource;
import su.plo.voice.api.client.config.addon.AddonConfig;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureEvent;
import su.plo.voice.api.client.event.audio.device.DeviceOpenEvent;
import su.plo.voice.api.client.event.audio.device.source.AlSourceClosedEvent;
import su.plo.voice.api.client.event.audio.device.source.AlSourceWriteEvent;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.util.AudioUtil;
import su.plo.voice.proto.data.pos.Pos3d;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

@Addon(
        id = "pv-addon-soundphysics",
        name = "gui.plasmovoice.soundphysics",
        scope = AddonLoaderScope.CLIENT,
        version = "1.0.0",
        authors = {"Apehum"}
)
public final class SoundPhysicsAddon implements AddonInitializer {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Map<AlSource, Long> lastCalculated = Maps.newConcurrentMap();
    private final Map<AlSource, float[]> lastPosition = Maps.newConcurrentMap();
    private final Pos3d playerPosition = new Pos3d();
    private final Pos3d lastPlayerPosition = new Pos3d();

    @Inject
    private PlasmoVoiceClient voiceClient;
    private AddonConfig config;

    private Method soundPhysicsInit;
    private Method soundPhysicsSetEnvironment;
    private Method soundPhysicsPlaySound;
    private Method soundPhysicsSetLastCategoryAndName;
    private Method soundPhysicsReverb;

    private Object masterCategory;

    private LoopbackSource loopbackSource;

    private BooleanConfigEntry enabledEntry;
    private BooleanConfigEntry microphoneReverbEnabledEntry;
    private DoubleConfigEntry microphoneReverbVolumeEntry;


    @Override
    public void onAddonInitialize() {
        try {
            // dependencies? nah
            // reflections? yep
            Class<?> clazz;
            try {
                clazz = Class.forName("com.sonicether.soundphysics.SoundPhysics");
            } catch (ClassNotFoundException e) {
                LOGGER.error("SoundPhysics is not installed");
                return;
            }

            soundPhysicsInit = clazz.getMethod("init");

            soundPhysicsSetLastCategoryAndName = Arrays.stream(clazz.getMethods())
                    .filter((method) -> method.getName().equals("setLastSoundCategoryAndName"))
                    .findFirst()
                    .orElse(null);

            Class<?> category = soundPhysicsSetLastCategoryAndName.getParameterTypes()[0];
            Object[] values = (Object[]) category.getMethod("values").invoke(null);
            this.masterCategory = values[0];

            try {
                soundPhysicsSetEnvironment = clazz.getMethod(
                        "setEnvironment",
                        int.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class
                );
            } catch (NoSuchMethodException ignored) {
                soundPhysicsSetEnvironment = Class.forName("com.sonicether.soundphysics.SPEfx").getMethod(
                        "setEnvironment",
                        int.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class
                );
            }

            soundPhysicsPlaySound = clazz.getMethod(
                    "onPlaySound",
                    double.class, double.class, double.class, int.class
            );
            soundPhysicsReverb = clazz.getMethod(
                    "onPlayReverb",
                    double.class, double.class, double.class, int.class
            );
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }

        // add menu widgets
        this.config = voiceClient.getAddonConfig(this);
        config.clear();

        this.enabledEntry = config.addToggle(
                "gui.plasmovoice.soundphysics.enabled",
                null,
                true
        );
        enabledEntry.addChangeListener(this::onToggle);

        if (soundPhysicsReverb != null) {
            this.microphoneReverbEnabledEntry = config.addToggle(
                    "gui.plasmovoice.soundphysics.mic_reverb",
                    "gui.plasmovoice.soundphysics.mic_reverb.tooltip",
                    true
            );
            microphoneReverbEnabledEntry.addChangeListener(this::onReverbToggle);

            this.microphoneReverbVolumeEntry = config.addVolumeSlider(
                    "gui.plasmovoice.soundphysics.mic_reverb_volume",
                    null,
                    "%",
                    1D, 0D, 2D
            );
        }
    }

    @EventSubscribe
    public void onDeviceOpen(@NotNull DeviceOpenEvent event) {
        if (!(event.getDevice() instanceof AlAudioDevice) ||
                !(event.getDevice() instanceof OutputDevice) ||
                !isEnabled()
        ) return;

        if (soundPhysicsInit != null) {
            try {
                soundPhysicsInit.invoke(null);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
    }

    @EventSubscribe
    public void onSourceClose(@NotNull AlSourceClosedEvent event) {
        lastCalculated.remove(event.getSource());
        lastPosition.remove(event.getSource());
    }

    @EventSubscribe
    public void onAudioCapture(@NotNull AudioCaptureEvent event) {
        if (!isEnabled() || !isMicrophoneReverbEnabled() || soundPhysicsReverb == null) return;

        if (voiceClient.getActivationManager().getActivations()
                .stream()
                .noneMatch(ClientActivation::isActive)
        ) return;

        boolean isStereo = event.getDevice().getFormat().getChannels() == 2;

        if (loopbackSource != null && loopbackSource.isStereo() != isStereo) {
            loopbackSource.close();
            this.loopbackSource = null;
        }

        if (loopbackSource == null) {
            try {
                this.loopbackSource = voiceClient.getSourceManager().createLoopbackSource(false);
                if (microphoneReverbVolumeEntry != null) {
                    loopbackSource.setVolumeEntry(microphoneReverbVolumeEntry);
                }
                loopbackSource.initialize(false);
            } catch (DeviceException e) {
                LOGGER.warn("Failed to initialize loopback source for SoundPhysics integration", e);
                return;
            }
        }

        short[] samples = new short[event.getSamples().length];
        System.arraycopy(event.getSamples(), 0, samples, 0, event.getSamples().length);

        if (isStereo) {
            samples = AudioUtil.convertToMonoShorts(samples);
        }

        samples = event.getDevice().processFilters(
                samples,
                (filter) -> isStereo && filter.getName().equals("stereo_to_mono")
        );
        loopbackSource.write(samples);
    }

    @EventSubscribe
    public void onSourceWrite(@NotNull AlSourceWriteEvent event) {
        if (!isEnabled()) return;

        AlSource alSource = event.getSource();
        alSource.getDevice().runInContextAsync(() -> {
            // don't process relative sources
            if (alSource.getInt(0x202) == 1) return;

            long lastCalculated = this.lastCalculated.getOrDefault(alSource, 0L);
            if (System.currentTimeMillis() - lastCalculated < 500L) return;

            float[] lastPosition = this.lastPosition.get(alSource);

            float[] position = new float[3];
            alSource.getFloatArray(0x1004, position); // AL_POSITION

            if (loopbackSource != null &&
                    loopbackSource.getSourceGroup().isPresent() &&
                    loopbackSource.getSourceGroup().get().getSources().contains(alSource)
            ) {
                soundPhysicsUpdate(alSource, lastPosition, position, soundPhysicsReverb);
            } else {
                soundPhysicsUpdate(alSource, lastPosition, position, soundPhysicsPlaySound);
            }
        });
    }

    private void soundPhysicsUpdate(AlSource alSource, float[] lastPosition, float[] position, Method soundPhysicsMethod) {
        if (evaluate(alSource, lastPosition, position) || soundPhysicsMethod == soundPhysicsReverb) {
            try {
                if (lastPosition == null) {
                    soundPhysicsSetEnvironment.invoke(null,
                            (int) alSource.getPointer(),
                            0F, 0F, 0F, 0F, 1F, 1F, 1F, 1F, 1F, soundPhysicsMethod == soundPhysicsReverb ? 0F : 1F
                    );
                }

                soundPhysicsSetLastCategoryAndName.invoke(null, masterCategory, "voicechat");
                soundPhysicsMethod.invoke(
                        null,
                        (double) position[0],
                        (double) position[1],
                        (double) position[2],
                        (int) alSource.getPointer()
                );

                this.lastPosition.put(alSource, position);
                setPlayerPosition(lastPlayerPosition);
                if (lastPosition != null) this.lastCalculated.put(alSource, System.currentTimeMillis());
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean evaluate(AlSource alSource, float[] lastPosition, float[] position) {
        return lastPosition == null ||
                !lastCalculated.containsKey(alSource) ||
                distance(position, lastPosition) > 0.25 ||
                distancePlayerTraveled() > 0.25;
    }

    private double distancePlayerTraveled() {
        setPlayerPosition(playerPosition);
        return Math.sqrt(playerPosition.distanceSquared(lastPlayerPosition));
    }

    private double distance(float[] position1, float[] position2) {
        double d = position1[0] - position2[0];
        double e = position1[1] - position2[1];
        double f = position1[2] - position2[2];

        return Math.sqrt(d * d + e * e + f * f);
    }

    private Pos3d setPlayerPosition(Pos3d position) {
        if (!UPlayer.hasPlayer()) return position;

        position.setX(UPlayer.getPosX());
        position.setY(UPlayer.getPosY());
        position.setZ(UPlayer.getPosZ());

        return position;
    }

    private void onToggle(boolean enabled) {
        voiceClient.getDeviceManager().getDevices(DeviceType.OUTPUT).forEach((device) -> {
            if (device instanceof AlAudioDevice) {
                try {
                    device.reload();
                    if (loopbackSource != null) {
                        loopbackSource.close();
                        this.loopbackSource = null;
                    }
                } catch (DeviceException e) {
                    LOGGER.warn("Failed to reload device", e);
                }
            }
        });
    }

    private void onReverbToggle(boolean enabled) {
        if (!enabled) {
            if (loopbackSource != null) {
                loopbackSource.close();
                this.loopbackSource = null;
            }
        }
    }

    private boolean isEnabled() {
        return enabledEntry != null && enabledEntry.value();
    }

    private boolean isMicrophoneReverbEnabled() {
        return microphoneReverbEnabledEntry != null && microphoneReverbEnabledEntry.value();
    }
}
