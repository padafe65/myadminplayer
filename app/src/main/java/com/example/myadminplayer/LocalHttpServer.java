package com.example.myadminplayer;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import fi.iki.elonen.NanoHTTPD;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@UnstableApi
public class LocalHttpServer extends NanoHTTPD {
    private static final String TAG = "LocalHttpServer";
    private final Context context;

    public LocalHttpServer(Context context, int port) {
        super(port);
        this.context = context;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        String indexStr = params.get("index");
        
        Log.d(TAG, "Chromecast pidió el video con índice: " + indexStr);

        if (indexStr == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Missing Index");
        }

        try {
            int index = Integer.parseInt(indexStr);
            PlaybackService service = PlaybackService.getInstance();
            if (service == null) {
                Log.e(TAG, "Error: El servicio de reproducción no está listo.");
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Service not ready");
            }
            
            List<MediaItem> items = service.getOriginalMediaItems();
            if (index < 0 || index >= items.size()) {
                Log.e(TAG, "Error: Índice " + index + " fuera de rango (Lista de tamaño " + items.size() + ")");
                return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Song index out of range");
            }

            MediaItem item = items.get(index);
            if (item.localConfiguration == null) {
                Log.e(TAG, "Error: El item no tiene configuración local.");
                return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "No local config");
            }

            Uri uri = item.localConfiguration.uri;
            Log.d(TAG, "Sirviendo archivo: " + uri);
            
            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType == null) mimeType = "video/mp4";
            
            long fileSize = -1;
            try (android.content.res.AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
                if (afd != null) fileSize = afd.getLength();
            } catch (Exception e) {
                Log.w(TAG, "No se pudo obtener el tamaño con AssetFileDescriptor: " + e.getMessage());
            }

            Map<String, String> headers = session.getHeaders();
            String rangeHeader = headers.get("range");
            
            if (rangeHeader != null && rangeHeader.startsWith("bytes=") && fileSize > 0) {
                String rangeValue = rangeHeader.substring(6);
                long start = 0;
                long end = fileSize - 1;
                int minusIndex = rangeValue.indexOf('-');
                try {
                    if (minusIndex >= 0) {
                        String startStr = rangeValue.substring(0, minusIndex);
                        if (!startStr.isEmpty()) start = Long.parseLong(startStr);
                        String endStr = rangeValue.substring(minusIndex + 1);
                        if (!endStr.isEmpty()) end = Long.parseLong(endStr);
                    }
                } catch (NumberFormatException ignored) {}
                
                if (start > end) return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "");
                
                long contentLength = end - start + 1;
                InputStream inputStream = context.getContentResolver().openInputStream(uri);
                if (inputStream == null) return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not found");
                
                long skipped = inputStream.skip(start);
                Log.d(TAG, "Enviando rango: " + start + "-" + end + " de " + fileSize + " (Saltados: " + skipped + ")");
                
                Response response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, inputStream, contentLength);
                response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
                response.addHeader("Accept-Ranges", "bytes");
                response.addHeader("Access-Control-Allow-Origin", "*");
                return response;
            }

            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not found");

            Log.d(TAG, "Enviando video completo: " + uri);
            Response response = newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, fileSize);
            response.addHeader("Accept-Ranges", "bytes");
            response.addHeader("Access-Control-Allow-Origin", "*");
            return response;
            
        } catch (Exception e) {
            Log.e(TAG, "ERROR CRÍTICO sirviendo video: " + e.getMessage(), e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error: " + e.getMessage());
        }
    }
}
