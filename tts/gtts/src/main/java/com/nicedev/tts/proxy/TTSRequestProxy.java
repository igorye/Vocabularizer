package com.nicedev.tts.proxy;


import com.nicedev.tts.service.TTSData;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

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

	//request audio stream
	public InputStream requestTTSStream( TTSData ttsData ) {
		String data = ttsData.toString();
		LOGGER.debug("TTS request: {}[{}]",
					 data.length() > 15 ? data.substring(0, 15) : data, data.length());
		String request = ttsData.textToSpeak;
		boolean isTTSConnection = false;
		if (!request.contains("://")) {
			request = TTS_REQUEST.encode(nextHost(), request, ttsData.accent, request.length());
		}
		HttpResponse<InputStream> response = null;
		try {
			LOGGER.debug("requesting {}", request);
			isTTSConnection = !request.endsWith(".wav");
			HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(request)).GET();
			builder = setHeaders(builder);
			final HttpRequest httpRequest = builder.build();
			response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
			return new BufferedInputStream(response.body());
		} catch (NullPointerException eConn) {
			LOGGER.warn("Could not set up connection to {}", request);
			return null;
		} catch (IOException e) {
			try {
				if(Objects.requireNonNull(response).statusCode() == 404) return null;
			} catch (Exception e1) {
				LOGGER.error("Error has occurred while proxying request to {}", e1.getMessage());
				return null;
			}
			if (isTTSConnection) rejectRecentHost(e);
			return requestTTSStream(ttsData);
		} catch (InterruptedException e) {
			LOGGER.error("Error has occurred while proxying request to {}", e.getMessage());
			if (isTTSConnection) rejectRecentHost(e);
			return requestTTSStream(ttsData);
		}
	}

	private HttpRequest.Builder setHeaders( HttpRequest.Builder builder ) {
		return builder.header("cache-control", "no-cache")
					  .header("Postman-Token", "b8fb19cd-d56c-81bc-bb79-8994ff6470eb")
					  .header("accept", "*/*")
					  .header("accept-encoding", "gzip, deflate")
					  .header("accept-language", "en-US,en;q=0.9,ru;q=0.8")
					  .header("user-agent",
							  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
									  "Chrome/91.0.4472.114 Safari/537.36"
					  );
	}

}
