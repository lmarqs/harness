package thecook.formats

interface Document {
    String getText()
    void seed(Map metadata)
    Map getMetadata()
    boolean isEmpty()
    String read(String identity, Map definition)
    String render(Object value)
    void write(String identity, Map definition, String content)
    void remove(String identity, Map definition)
    void release(String identity, Map definition)
}
