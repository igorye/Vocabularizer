package com.nicedev.vocabularizer.services.proxy;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

public class TranslateRequestProxy extends GoogleRequestProxy {

	private static TranslateRequestProxy  instance = null;

	private TranslateRequestProxy() {
		super(GTSRequest.TRANSLATE_DETAILED);
	}

	public static TranslateRequestProxy getInstatnce() {
		if(instance == null) instance = new TranslateRequestProxy();
		return instance;
	}

	public InputStream requestTranslationStream(String translationRequest, String sourceLang, String targetLang) {
		String request = "";
		try {
			request = URLEncoder.encode(translationRequest, "UTF-8");
			request = String.format(TTS_REQUEST_FMT.format, nextHost(), sourceLang, targetLang, request,
					GoogleTranslateTokenGenerator.getToken(translationRequest));
		} catch (UnsupportedEncodingException e) {
			return requestTranslationStream(translationRequest, sourceLang, targetLang);
		}
		try {
			HttpURLConnection conn = getRequestConnection(request);
			prepareConnProps(conn);
			return new BufferedInputStream(conn.getInputStream());
		} catch (IOException e) {
			rejectRecentHost(e);
			return requestTranslationStream(translationRequest, sourceLang, targetLang);
		}
	}



}
