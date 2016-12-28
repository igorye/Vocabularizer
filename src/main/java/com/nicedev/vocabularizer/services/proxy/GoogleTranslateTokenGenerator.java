package com.nicedev.vocabularizer.services.proxy;

/*
*
*              Programmed by Antonio Blescia(www.blesciasw.it)
*                             No Comment Lab
*                MD5 Hash: 10e1b66fec05ca2fdddc9093f5f3073c
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

public class GoogleTranslateTokenGenerator{

	public static int xr(int a, String b)
	{
		for (int c = 0; c < b.length() - 2; c += 3) {
			int d = b.charAt(c + 2);
			d = d >= 'a' ? d - 87 : Integer.parseInt(Character.toString((char) d));
			d = '+' == b.charAt(c + 1) ? a >>> d : a << d;
			a = '+' == b.charAt(c) ? (int) (a + d & 4294967295L) : a ^ d;
		}
		return a;
	}

	public static String getToken(String text)
	{
		/* Available from https://translate.google.it/ page through window.TKK property */
		String yr = "407851.1338846116";

		String b = yr;
		String d = Character.toString ((char) 116);
		String c = Character.toString ((char) 107);
		String[] vD = new String[]{d, c};

		c = "&" + String.join("", vD) + "=";
		String[] cD = b.split("\\.");
		Integer iB = Integer.parseInt(cD[0]);
		Integer[] e = new Integer[text.length()];

		int f = 0;
		for(int g = 0; g < text.length(); g++ ) {
			int l = text.charAt(g);
			if (128 > l) {
				e[f++] = l;
			} else {
				if(2048 > l) {
					e[f++] = e[f++] = l >> 6 | 192 ;
				} else {
					if(55296 == (l & 64512) && g + 1 < text.length() && 56320 == (text.charAt(g + 1) & 64512)) {
						l = 65536 + ((l & 1023) << 10) + (text.charAt(++g) & 1023);
						e[f++] = l >> 18 | 240;
						e[f++] = l >> 12 & 63 | 128;
					} else {
						e[f++] = l >> 12 | 224;
						e[f++] = l >> 6 & 63 | 128;
					}
				}
				e[f++] = l & 63 | 128;
			}
		}
		int a = iB;
		for(int ff = 0; ff < e.length; ff++) {
			a+=e[ff];
			a = xr(a, "+-a^+6");
		}
		a = xr(a, "+-3^+b+-f");
		a ^= Integer.parseInt(cD[1]);
		long aa = (a & 2147483647L+2147483648L);
		aa %= 1E6;
		String token =  c + (aa + "." + (aa ^ iB));
		return token;
	}
}