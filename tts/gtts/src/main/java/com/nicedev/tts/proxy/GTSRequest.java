package com.nicedev.tts.proxy;

/**
 * Google Translate Service enumeration
 */
public enum GTSRequest {
	// https://translate.google.al/translate_tts?ie=UTF-8&q=text&client=tw-ob&tl=en
	// First %s is a service host. Second is a query to TTS. Third is accent. Fourth is a text length
	TEXT_TO_SPEECH ("https://%s/translate_tts?ie=UTF-8&q=%s&tl=en-%s&total=%d&idx=2&textlen=%d&client=tw-ob"),
	TRANSLATE_BRIEF("https://%s/translate_a/t?client=j&sl=%s&tl=%s&hl=%2$s&v=1.0&text={%s}"),
	TRANSLATE_DETAILED("https://%s/translate_a/single?client=t&sl=%s&tl=%s&hl=%2$s&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca" +
			          "&dt=rw&dt=rm&dt=ss&dt=t&ie=UTF-8&oe=UTF-8&source=btn&srcrom=0&ssel=0&tsel=0&kc=0" +
			          "&q=%s&tk=%s");


	final public String format;

	GTSRequest(String requestFmt) {
		this.format = requestFmt;
	}
}
