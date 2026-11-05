package com.michatec.radio.extensions.java;

import android.util.Log;
import com.michatec.radio.Keys;
import com.michatec.radio.core.Station;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * StationHealthChecker.class
 */
public class StationHealthChecker {

    private static final String TAG = StationHealthChecker.class.getSimpleName();
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public record HealthResult(boolean online, int responseCode, long latencyMs, String contentType,
                               String errorMessage) {
    }

    public interface OnStationHealthCheckedListener {
        void onHealthChecked(Station station, HealthResult result);
    }

    /**
     * Checks the health of a station asynchronously using an ExecutorService.
     */
    public static void checkStationHealth(final Station station, final OnStationHealthCheckedListener listener) {
        if (station == null || station.getStreamUri().isEmpty()) {
            if (listener != null) {
                listener.onHealthChecked(station, new HealthResult(false, -1, 0, "", "Invalid stream URI"));
            }
            return;
        }

        executorService.execute(() -> {
            long startTime = System.currentTimeMillis();
            boolean online;
            int responseCode;
            String contentType = Keys.MIME_TYPE_UNSUPPORTED;
            String errorMessage = null;

            HttpURLConnection connection = null;
            try {
                URL url = new URL(station.getStreamUri());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", Keys.INSTANCE.getUSER_AGENT());
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("GET");

                responseCode = connection.getResponseCode();
                long latency = System.currentTimeMillis() - startTime;

                if (responseCode >= 200 && responseCode < 400) {
                    online = true;
                    String rawContentType = connection.getContentType();
                    if (rawContentType != null && !rawContentType.isEmpty()) {
                        contentType = rawContentType.split(";")[0].trim();
                    }
                } else {
                    online = false;
                    errorMessage = "HTTP Error: " + responseCode;
                }

                final HealthResult result = new HealthResult(online, responseCode, latency, contentType, errorMessage);
                if (listener != null) {
                    listener.onHealthChecked(station, result);
                }
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - startTime;
                Log.e(TAG, "Error checking station health: " + e.getMessage(), e);
                final HealthResult result = new HealthResult(false, -1, latency, Keys.MIME_TYPE_UNSUPPORTED, e.getMessage());
                if (listener != null) {
                    listener.onHealthChecked(station, result);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
}
