package thecook.formats

import thecook.rendering.RegionLocation

interface Document {
  String getText()
  void seed(Map metadata)
  Map getMetadata()
  boolean isEmpty()
  String read(String identity, RegionLocation location)
  String render(Object value)
  void write(String identity, RegionLocation location, String content)
  void remove(String identity, RegionLocation location)
  void release(String identity, RegionLocation location)
}
