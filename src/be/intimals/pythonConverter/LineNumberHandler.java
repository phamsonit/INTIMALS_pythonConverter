package be.intimals.pythonConverter;


import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Document;

import java.util.Stack;

public class LineNumberHandler extends DefaultHandler {

    final Stack<Element> elementStack = new Stack<Element>();
    final StringBuilder textBuffer = new StringBuilder();
    private Locator locator;
    private Document doc;


    public LineNumberHandler(Document doc) {
        this.doc = doc;
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        this.locator = locator; // Save the locator, so that it can be used
        // later for line tracking when traversing
        // nodes.
    }

    @Override
    public void startElement(final String uri, final String localName,
                             final String qName, final Attributes attributes) throws SAXException {
        addTextIfNeeded();
        final Element el = doc.createElement(qName);
        for (int i = 0; i < attributes.getLength(); i++) {
            el.setAttribute(attributes.getQName(i), attributes.getValue(i));
        }
        el.setUserData(PositionalXMLReader.LINE_NUMBER_KEY_NAME,
                String.valueOf(this.locator.getLineNumber()), null);
        el.setUserData(PositionalXMLReader.COL_NUMBER_KEY_NAME,
                String.valueOf(this.locator.getColumnNumber()), null);
        elementStack.push(el);
    }

    @Override
    public void endElement(final String uri, final String localName,
                           final String qName) {
        addTextIfNeeded();
        final Element closedEl = elementStack.pop();
        if (elementStack.isEmpty()) { // Is this the root element?
            doc.appendChild(closedEl);
        } else {
            final Element parentEl = elementStack.peek();
            parentEl.appendChild(closedEl);
        }
    }

    @Override
    public void characters(final char ch[], final int start, final int length)
            throws SAXException {
        textBuffer.append(ch, start, length);
    }

    // Outputs text accumulated under the current node
    private void addTextIfNeeded() {
        if (textBuffer.length() > 0) {
            final Element el = elementStack.peek();
            final Node textNode = doc.createTextNode(textBuffer.toString());
            el.appendChild(textNode);
            textBuffer.delete(0, textBuffer.length());
        }
    }
}