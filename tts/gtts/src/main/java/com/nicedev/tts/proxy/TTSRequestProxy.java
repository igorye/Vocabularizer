package com.nicedev.tts.proxy;


import com.nicedev.tts.service.TTSData;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

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
	public InputStream requestTTSStream( TTSData ttsData ) {
		String data = ttsData.toString();
		LOGGER.debug("TTS request: {}[{}]",
		             data.length() > 15 ? data.substring(0, 15) : data, data.length());
		String request = ttsData.textToSpeak;
		boolean isTTSConnection = false;
		if (!request.contains("://"))
			request = TTS_REQUEST.encode(nextHost(), request, ttsData.accent, request.length());
		HttpURLConnection conn = null;
		try {
			conn = getRequestConnection(request);
			isTTSConnection = !request.endsWith(".wav");
			if (isTTSConnection) {
				prepareConnProps(conn);
			} else if (conn.getResponseCode() != 200) {
				final String redirect = Optional.ofNullable(conn.getHeaderField("Location"))
//												.orElse(request.replaceFirst("http", "https"));
												.orElse("");
				conn = getRequestConnection(redirect);
			}
//			return new ByteArrayInputStream(fetchStream(conn.getInputStream()));
			return new BufferedInputStream(conn.getInputStream(), conn.getContentLength());
		} catch (NullPointerException eConn) {
			LOGGER.warn("Could not set up connection to {}", request);
			return null;
		} catch (IOException e) {
			try {
				if(Objects.requireNonNull(conn).getResponseCode() == 404) return null;
			} catch (Exception e1) {
				LOGGER.error("Error has occured while proxying request to {}", e1.getMessage());
				return null;
			}
			if (isTTSConnection) rejectRecentHost(e);
			return requestTTSStream(ttsData);
		}
	}

}
