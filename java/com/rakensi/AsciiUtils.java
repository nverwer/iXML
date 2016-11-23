package com.rakensi.trie;

import java.io.UnsupportedEncodingException;
import java.text.Normalizer;

/**
 * See [http://docs.oracle.com/javase/6/docs/api/java/text/Normalizer.html],
 * [http://www.unicode.org/reports/tr15/tr15-23.html],
 * [http://stackoverflow.com/questions/2096667/convert-unicode-to-ascii-without-changing-the-string-length-in-java].
 * The following is taken from [http://www.rgagnon.com/javadetails/java-0456.html].
 */
public class AsciiUtils {
  private static final String PLAIN_ASCII =
    "AaEeIiOoUu"    // grave
  + "AaEeIiOoUuYy"  // acute
  + "AaEeIiOoUuYy"  // circumflex
  + "AaOoNn"        // tilde
  + "AaEeIiOoUuYy"  // umlaut
  + "Aa"            // ring
  + "Cc"            // cedilla
  + "OoUu"          // double acute
  ;

  private static final String UNICODE =
    "\u00C0\u00E0\u00C8\u00E8\u00CC\u00EC\u00D2\u00F2\u00D9\u00F9"
  + "\u00C1\u00E1\u00C9\u00E9\u00CD\u00ED\u00D3\u00F3\u00DA\u00FA\u00DD\u00FD"
  + "\u00C2\u00E2\u00CA\u00EA\u00CE\u00EE\u00D4\u00F4\u00DB\u00FB\u0176\u0177"
  + "\u00C3\u00E3\u00D5\u00F5\u00D1\u00F1"
  + "\u00C4\u00E4\u00CB\u00EB\u00CF\u00EF\u00D6\u00F6\u00DC\u00FC\u0178\u00FF"
  + "\u00C5\u00E5"
  + "\u00C7\u00E7"
  + "\u0150\u0151\u0170\u0171"
  ;

  // Private constructor, can't be instantiated.
  private AsciiUtils() { }
  
  // Normalize a string to ASCII [http://stackoverflow.com/a/2097224/1021892].
  public static String normalize(String s) {
    s = s.replaceAll("[\u2018`\u2032\u00B4\u2019]", "'")
         .replaceAll("[\u201C\u201D]", "\"")
         .replaceAll("[\u2010\u2011\u2012\u2013\u2014\u2015\u2212]",  "-");
    String sn = Normalizer.normalize(s, Normalizer.Form.NFKD);
    String sr;
    try {
      sr = new String(sn.replaceAll("[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+", "").getBytes("ascii"), "ascii");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    return sr;
  }
  
  // Normalize a character to ASCII
  public static char normalize(char c) {
    return normalize(Character.toString(c)).charAt(0);
  }
  
  // Convert all characters in a string to ASCII codes 0x20 - 0x7E and remove punctuation.
  // Only letters and digits are kept, other characters are removed.
  public static String convertToLowAsciiWithoutPunctuation(String s) {
    if (s == null) return null;
    StringBuilder sb = new StringBuilder();
    int n = s.length();
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      int pos = UNICODE.indexOf(c);
      if (pos > -1) {
        sb.append(PLAIN_ASCII.charAt(pos));
      } else if (isLowAsciiWithoutPunctuation(c)) {
        sb.append(c);
      }
    }
    return sb.toString();
  }
  
  public static boolean isLowAsciiWithoutPunctuation(char c) {
    return (Character.isLetterOrDigit(c) || c == ' ') && c >= 0x20 && c < 0x7F;
  }
  
}
