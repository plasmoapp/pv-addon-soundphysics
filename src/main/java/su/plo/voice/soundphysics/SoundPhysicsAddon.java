package su.plo.voice.soundphysics;

import org.jetbrains.annotations.NotNull;
import su.plo.config.entry.BooleanConfigEntry;
import su.plo.config.entry.DoubleConfigEntry;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.slib.api.logging.McLazyLogger;
import su.plo.slib.api.logging.McLoggerFactory;
import su.plo.slib.api.position.Pos3d;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.addon.annotation.Dependency;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.audio.device.AlContextAudioDevice;
import su.plo.voice.api.client.audio.device.DeviceException;
import su.plo.voice.api.client.audio.device.DeviceType;
import su.plo.voice.api.client.audio.device.OutputDevice;
import su.plo.voice.api.client.audio.device.source.AlSource;
import su.plo.voice.api.client.audio.source.LoopbackSource;
import su.plo.voice.api.client.config.addon.AddonConfig;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;
import su.plo.voice.api.client.event.audio.device.DeviceOpenEvent;
import su.plo.voice.api.client.event.audio.device.source.AlSourceClosedEvent;
import su.plo.voice.api.client.event.audio.device.source.AlSourceWriteEvent;
import su.plo.voice.api.event.EventSubscribe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Addon(
        id = "pv-addon-soundphysics",
        name = "gui.plasmovoice.soundphysics",
        scope = AddonLoaderScope.CLIENT,
        version = BuildConstants.VERSION,
        authors = {"Apehum"},
        dependencies = {
                @Dependency(id = "sound_physics_remastered")
        }
)
public final class SoundPhysicsAddon implements AddonInitializer {

    private static final McLazyLogger LOGGER = McLoggerFactory.createLogger("SoundPhysicsAddon");

    private final Map<AlSource, Long> lastCalculated = new ConcurrentHashMap<>();
    private final Map<AlSource, float[]> lastPosition = new ConcurrentHashMap<>();
    private final Pos3d playerPosition = new Pos3d();
    private final Pos3d lastPlayerPosition = new Pos3d();

    @InjectPlasmoVoice
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
                "enabled",
                McTextComponent.translatable("gui.plasmovoice.soundphysics.enabled"),
                null,
                true
        );
        enabledEntry.addChangeListener(this::onToggle);

        if (enabledEntry.value()) {
            voiceClient.getConfig().getVoice().getSoundOcclusion().setDisabled(true);
        }

        if (soundPhysicsReverb != null) {
            this.microphoneReverbEnabledEntry = config.addToggle(
                    "mic_reverb",
                    McTextComponent.translatable("gui.plasmovoice.soundphysics.mic_reverb"),
                    McTextComponent.translatable("gui.plasmovoice.soundphysics.mic_reverb.tooltip"),
                    true
            );
            microphoneReverbEnabledEntry.addChangeListener(this::onReverbToggle);

            this.microphoneReverbVolumeEntry = config.addVolumeSlider(
                    "mic_reverb_volume",
                    McTextComponent.translatable("gui.plasmovoice.soundphysics.mic_reverb_volume"),
                    null,
                    "%",
                    1D, 0D, 2D
            );
        }
    }

    @EventSubscribe
    public void onDeviceOpen(@NotNull DeviceOpenEvent event) {
        if (!(event.getDevice() instanceof AlContextAudioDevice) ||
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
    public void onAudioCaptureProcessed(@NotNull AudioCaptureProcessedEvent event) {
        if (!isEnabled() || !isMicrophoneReverbEnabled() || soundPhysicsReverb == null) return;

        if (voiceClient.getActivationManager().getActivations()
                .stream()
                .filter(ClientActivation::isProximity)
                .noneMatch(ClientActivation::isActive)
        ) return;

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

        short[] samples = event.getProcessed().getSamples(false);
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
                    loopbackSource.getSource().isPresent() &&
                    loopbackSource.getSource().get().equals(alSource)
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
        Class<?> uPlayer;
        try {
            uPlayer = Class.forName("gg.essential.universal.wrappers.UPlayer");
        } catch (Exception ignored) {
            try {
                uPlayer = Class.forName("su.plo.voice.universal.wrappers.UPlayer");
            } catch (Exception ignored1) {
                return position;
            }
        }

        try {
            boolean hasPlayer = (boolean) uPlayer.getMethod("hasPlayer").invoke(null);

            if (!hasPlayer) return position;

            position.setX((double) uPlayer.getMethod("getPosX").invoke(null));
            position.setY((double) uPlayer.getMethod("getPosY").invoke(null));
            position.setZ((double) uPlayer.getMethod("getPosZ").invoke(null));
        } catch (Exception ignored) {
        }

        return position;
    }

    private void onToggle(boolean enabled) {
        voiceClient.getConfig().getVoice().getSoundOcclusion().setDisabled(enabled);

        voiceClient.getDeviceManager().getOutputDevice()
                .ifPresent(device -> {
                    try {
                        device.reload();
                        if (loopbackSource != null) {
                            loopbackSource.close();
                            this.loopbackSource = null;
                        }
                    } catch (DeviceException e) {
                        LOGGER.warn("Failed to reload device", e);
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
