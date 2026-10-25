package com.tripletriad;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Effets + musique. Cherche d'abord les fichiers dans assets/Sounds ; s'ils sont absents,
 * génère des sons synthétiques (donc ça sonne même sans aucun fichier audio).
 */
public final class Sound {
    public enum Fx {
        START("sound-start.wav"), PLACE("sound-card.wav"), FLIP("flip.wav"),
        TURN("sound-turn.wav"), WIN("win.wav"), LOSE("lose.wav"), COMBO("combo.wav");
        final String file;
        Fx(String f) { file = f; }
    }

    private static final int RATE = 22050;
    private final Context ctx;
    private final boolean enabled;
    private final SoundPool pool;
    private final Map<Fx, Integer> ids = new EnumMap<>(Fx.class);
    private final Set<Integer> loaded = new HashSet<>();
    private MediaPlayer bgm;

    public Sound(Context ctx) {
        this.ctx = ctx;
        enabled = ctx.getSharedPreferences("tt", Context.MODE_PRIVATE).getBoolean("sound", true);
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        pool.setOnLoadCompleteListener((p, id, status) -> { if (status == 0) loaded.add(id); });
        if (enabled) for (Fx fx : Fx.values()) load(fx);
    }

    private void load(Fx fx) {
        int id;
        try {
            AssetFileDescriptor fd = ctx.getAssets().openFd("Sounds/" + fx.file);
            id = pool.load(fd, 1);
            fd.close();
        } catch (Exception e) {
            try {
                File f = new File(ctx.getCacheDir(), "tt_" + fx.name() + ".wav");
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(wav(synth(fx))); }
                id = pool.load(f.getAbsolutePath(), 1);
            } catch (Exception e2) { return; }
        }
        ids.put(fx, id);
    }

    public void play(Fx fx) {
        Integer id = ids.get(fx);
        if (enabled && id != null && loaded.contains(id)) pool.play(id, 1f, 1f, 1, 0, 1f);
    }

    public void startMusic() {
        if (!enabled) return;
        if (bgm != null) { if (!bgm.isPlaying()) bgm.start(); return; }
        try (AssetFileDescriptor fd = ctx.getAssets().openFd("Sounds/bgm.mp3")) {
            bgm = new MediaPlayer();
            bgm.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            bgm.setLooping(true);
            bgm.setVolume(0.35f, 0.35f);
            bgm.prepare();
            bgm.start();
        } catch (Exception e) { // pas de musique fournie
            if (bgm != null) bgm.release();
            bgm = null;
        }
    }

    public void pauseMusic() { if (bgm != null && bgm.isPlaying()) bgm.pause(); }

    public void release() {
        if (bgm != null) { bgm.release(); bgm = null; }
        pool.release();
    }

    // ---------- synthèse ----------

    private static byte[] synth(Fx fx) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        switch (fx) {
            case START: tone(o, 523, 523, 100, .5); tone(o, 659, 659, 100, .5);
                        tone(o, 784, 784, 100, .5); tone(o, 1047, 1047, 280, .5); break;
            case PLACE: tone(o, 200, 90, 120, .8); break;
            case FLIP:  tone(o, 500, 1100, 170, .45); break;
            case TURN:  tone(o, 988, 988, 90, .45); tone(o, 1319, 1319, 170, .45); break;
            case WIN:   tone(o, 523, 523, 140, .5); tone(o, 659, 659, 140, .5);
                        tone(o, 784, 784, 140, .5); tone(o, 1047, 1047, 420, .5); break;
            case COMBO: tone(o, 523, 523, 70, .5); tone(o, 659, 659, 70, .5); tone(o, 784, 784, 70, .5);
                        tone(o, 1047, 1047, 70, .5); tone(o, 1319, 1319, 220, .5); break;
            default:    tone(o, 392, 392, 220, .5); tone(o, 330, 330, 220, .5); tone(o, 262, 262, 450, .5);
        }
        return o.toByteArray();
    }

    private static void tone(ByteArrayOutputStream o, double f0, double f1, int ms, double vol) {
        int n = RATE * ms / 1000;
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            phase += 2 * Math.PI * (f0 + (f1 - f0) * t) / RATE;
            double env = Math.pow(1 - t, 1.5) * Math.min(1.0, i / 60.0);
            short s = (short) (Math.sin(phase) * vol * env * 32767);
            o.write(s & 0xFF);
            o.write((s >> 8) & 0xFF);
        }
    }

    private static byte[] wav(byte[] pcm) {
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()); b.putInt(36 + pcm.length); b.put("WAVE".getBytes());
        b.put("fmt ".getBytes()); b.putInt(16); b.putShort((short) 1); b.putShort((short) 1);
        b.putInt(RATE); b.putInt(RATE * 2); b.putShort((short) 2); b.putShort((short) 16);
        b.put("data".getBytes()); b.putInt(pcm.length); b.put(pcm);
        return b.array();
    }
}
