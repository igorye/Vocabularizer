package com.nicedev.gtts.proxy;


import com.nicedev.gtts.service.TTSData;

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
	}
	
	private TTSRequestProxy() {
		super(GTSRequest.TEXT_TO_SPEECH);
	}
	
	static public TTSRequestProxy getInstance() {
		return TTSRequestProxyHolder.instance;
	}
	
	//request mp3 stream
	public InputStream requestTTSStream(TTSData pronunciationData) {
		String request = pronunciationData.pronunciationSource;
		boolean isTTSConnection = false;
		try {
			if (!request.contains("://"))
				request = String.format(TTS_REQUEST_FMT.format, nextHost(), URLEncoder.encode(request, "UTF-8"),
				                        pronunciationData.accent, request.length(), request.length());
		} catch (UnsupportedEncodingException e) {
			return requestTTSStream(pronunciationData);
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
			LOGGER.warn("Could not set up connection to {}", request);
			return null;
		} catch (IOException e) {
			try {
				if(conn.getResponseCode() == 404) return null;
			} catch (IOException e1) {
				LOGGER.error("Error has occured while proxying request to {}", e1.getMessage());
				return null;
			}
			if (isTTSConnection) rejectRecentHost(e);
			return requestTTSStream(pronunciationData);
		}
	}
}
