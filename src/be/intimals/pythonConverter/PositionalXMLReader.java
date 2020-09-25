package be.intimals.pythonConverter;

import org.xml.sax.SAXException;

import org.w3c.dom.Document;
import javax.xml.parsers.*;
import java.io.IOException;
import java.io.InputStream;

public class PositionalXMLReader {
    final static String LINE_NUMBER_KEY_NAME = "lineNumber";
    final static String COL_NUMBER_KEY_NAME = "columnNumber";

    public static Document readXML(final InputStream is)
            throws IOException, SAXException {
        final Document doc;
        SAXParser parser;
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            parser = factory.newSAXParser();
            final DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
                    .newInstance();
            final DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            doc = docBuilder.newDocument();
        } catch (final ParserConfigurationException e) {
            throw new RuntimeException("Can't create SAX parser / DOM builder.", e);
        }
        LineNumberHandler handler = new LineNumberHandler(doc);
        parser.parse(is, handler);

        return doc;
    }
}
