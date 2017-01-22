package eval;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Form implements Comparable<Form> {

  private final List<Glyph> glyphs;
  
  public Form(List<Glyph> glyphs) {
    this.glyphs = glyphs;
  }
  
  public static Form charsAsGlyphs(String str) {
    List<Glyph> glyphs = new ArrayList<Glyph>();
    for (int i = 0; i < str.length(); i++) {
      glyphs.add(new Glyph(str.substring(i, i+1)));
    }
    return new Form(glyphs);
  }
  
  public static Form wordsAsGlyphs(List<String> words) {
    List<Glyph> glyphs = new ArrayList<Glyph>();
    for (int i = 0; i < words.size(); i++) {
      glyphs.add(new Glyph(words.get(i)));
    }
    return new Form(glyphs);
  }
  
  public Form substring(int start) {
    return substring(start, length());
  }
  
  public Form substring(int start, int end) {
    return new Form(glyphs.subList(start, end));
  }
  
  public int length() {
    return glyphs.size();
  }

  public Glyph charAt(int index) {
    return glyphs.get(index);
  }
  
  public Form append(Form other) {
    List<Glyph> newGlyphs = new ArrayList<Glyph>();
    newGlyphs.addAll(this.glyphs);
    newGlyphs.addAll(other.glyphs);
    return new Form(newGlyphs);
  }
  
  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof Form)) {
      return false;
    }
    return this.glyphs.equals(((Form)other).glyphs);
  }
  
  @Override
  public int hashCode() {
    return this.glyphs.hashCode();
  }

  @Override
  public String toString() {
    String ret = "";
    for (Glyph glyph : glyphs) {
      ret += glyph.toString();
    }
    return ret;
  }

  public String toStringWithSpaces() {
    String ret = "";
    for (Glyph glyph : glyphs) {
      ret += glyph.toString() + " ";
    }
    return ret;
  }

  @Override
  public int compareTo(Form o) {
    return compareCollections(this.glyphs, o.glyphs);
  }
  
  public static <T extends Comparable<T>> int compareCollections(Iterable<T> col1, Iterable<T> col2) {
    Iterator<T> first = col1.iterator();
    Iterator<T> second = col2.iterator();
    while (first.hasNext() && second.hasNext()) {
      int result = first.next().compareTo(second.next());
      if (result != 0) {
        return result;
      }
    }
    if (!first.hasNext() && !second.hasNext()) {
      return 0;
    }
    // Longer one comes second
    return (first.hasNext() ? 1 : -1);
  }
}
