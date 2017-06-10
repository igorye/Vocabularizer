package com.nicedev.vocabularizer.services.proxy;

import com.nicedev.util.SimpleLog;
import com.nicedev.vocabularizer.services.sound.PronunciationData;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

public class TTSRequestProxy extends GoogleRequestProxy {

    private static class TTSRequestProxyHolder {
        static final TTSRequestProxy instance = new TTSRequestProxy();
    };

    private TTSRequestProxy() {
        super(GTSRequest.TEXT_TO_SPEECH);
    }

    static public TTSRequestProxy getInstance() {
        return TTSRequestProxyHolder.instance;
    }

    //request mp3 stream
    public InputStream requestPronunciationStream(PronunciationData pronunciationData) {
        String request = pronunciationData.pronunciationSource;
        boolean isTTSConnection = false;
        try {
            if (!request.contains("://"))
                request = String.format(TTS_REQUEST_FMT.format, nextHost(), URLEncoder.encode(request, "UTF-8"), pronunciationData.accent, request.length(), request.length());
        } catch (UnsupportedEncodingException e) {
            return requestPronunciationStream(pronunciationData);
        }
        HttpURLConnection conn = null;
        try {
            conn = getRequestConnection(request);
            isTTSConnection = conn instanceof HttpsURLConnection;
            if (isTTSConnection)
                prepareConnProps(conn);
            int streamSize = conn.getContentLength();
            return new BufferedInputStream(conn.getInputStream(), streamSize);
        } catch (NullPointerException eConn) {
            return null;
        } catch (IOException e) {
            try {
                if(conn.getResponseCode() == 404) return null;
            } catch (IOException e1) {
                SimpleLog.log("Error has occured while proxying request to %s", e1.getMessage());
                return null;
            }
            if (isTTSConnection) rejectRecentHost(e);
            return requestPronunciationStream(pronunciationData);
        }
    }
}
