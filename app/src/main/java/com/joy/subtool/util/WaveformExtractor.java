package com.joy.subtool.util;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WaveformExtractor {

    private static final int TARGET_SAMPLES = 200;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onWaveformReady(float[] amplitudes);
        void onError(Exception e);
    }

    public static void extract(Context context, Uri uri, Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                float[] result = extractSync(context, uri);
                mainHandler.post(() -> callback.onWaveformReady(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private static float[] extractSync(Context context, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, uri, null);

        int audioTrack = -1;
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrack = i;
                format = f;
                break;
            }
        }

        if (audioTrack < 0 || format == null) {
            extractor.release();
            return new float[0];
        }

        extractor.selectTrack(audioTrack);
        String mime = format.getString(MediaFormat.KEY_MIME);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                ? format.getLong(MediaFormat.KEY_DURATION) : 0;

        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        long totalSamples = (durationUs * sampleRate) / 1_000_000L;
        int samplesPerBucket = Math.max(1, (int) (totalSamples / TARGET_SAMPLES));

        float[] buckets = new float[TARGET_SAMPLES];
        long[] bucketCounts = new long[TARGET_SAMPLES];
        long sampleIndex = 0;

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        boolean inputDone = false;
        boolean outputDone = false;

        while (!outputDone) {
            // Feed input
            if (!inputDone) {
                int inputIdx = codec.dequeueInputBuffer(10_000);
                if (inputIdx >= 0) {
                    ByteBuffer inputBuf = codec.getInputBuffer(inputIdx);
                    if (inputBuf != null) {
                        int read = extractor.readSampleData(inputBuf, 0);
                        if (read < 0) {
                            codec.queueInputBuffer(inputIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            codec.queueInputBuffer(inputIdx, 0, read, pts, 0);
                            extractor.advance();
                        }
                    }
                }
            }

            // Read output
            int outputIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000);
            if (outputIdx >= 0) {
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true;
                }
                ByteBuffer outputBuf = codec.getOutputBuffer(outputIdx);
                if (outputBuf != null && bufferInfo.size > 0) {
                    outputBuf.order(ByteOrder.nativeOrder());
                    outputBuf.position(bufferInfo.offset);
                    int shortCount = bufferInfo.size / 2;
                    for (int i = 0; i < shortCount; i += channels) {
                        short sample = outputBuf.getShort();
                        // Skip remaining channels
                        for (int ch = 1; ch < channels && i + ch < shortCount; ch++) {
                            outputBuf.getShort();
                        }
                        float amplitude = Math.abs(sample) / 32768f;
                        int bucketIdx = (int) (sampleIndex / samplesPerBucket);
                        if (bucketIdx >= TARGET_SAMPLES) bucketIdx = TARGET_SAMPLES - 1;
                        buckets[bucketIdx] += amplitude;
                        bucketCounts[bucketIdx]++;
                        sampleIndex++;
                    }
                }
                codec.releaseOutputBuffer(outputIdx, false);
            }
        }

        codec.stop();
        codec.release();
        extractor.release();

        // Average and normalize
        float max = 0f;
        for (int i = 0; i < TARGET_SAMPLES; i++) {
            if (bucketCounts[i] > 0) {
                buckets[i] /= bucketCounts[i];
            }
            if (buckets[i] > max) max = buckets[i];
        }
        if (max > 0f) {
            for (int i = 0; i < TARGET_SAMPLES; i++) {
                buckets[i] /= max;
            }
        }

        return buckets;
    }
}
