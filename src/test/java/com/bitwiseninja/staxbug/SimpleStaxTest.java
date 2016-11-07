package com.bitwiseninja.staxbug;

import com.igormaznitsa.jute.annotations.JUteTest;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.stream.EventFilter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * <p>
 *     Unit tests using a 100MB XML file. Uses JUte to kickoff tests in separate JVMs.
 *     Should be run from Maven so that classloader JVM options are respected
 * </p>
 * <p>
 *     See <code>src/test/resources/dblp.xsd</code> for the XML structure
 * </p>
 */
public class SimpleStaxTest {

    private static final String INPUT_XML_FILE = "target/test-classes/dblp.xml.gz";
    private static final String TAG_TO_FILTER = "dblp";
    private static final String JVM_XBOOTCLASSPATH_KEY = "-Xbootclasspath/p:";
    private static final String MAVEN_GENERATED_PATCH_LOCATION = "./target/classes/";
    private static final String JVM_ARG_PATCH = JVM_XBOOTCLASSPATH_KEY + MAVEN_GENERATED_PATCH_LOCATION;
    private static final String JVM_MAX_MEMORY_SIZE = "-Xmx256m";
    private static final String PATCH_FILE_PACKAGE = "com/sun/xml/internal/stream/";
    private static final String PATCHED_CLASS = "EventFilterSupport.class";


    /**
     * Stock JVM test for nextEvent. Throws StackOverflowError because algorithm uses recursion
     */
    @Test(expected = StackOverflowError.class)
    @JUteTest(jvmOpts = {JVM_MAX_MEMORY_SIZE})
    public void testNextEventRegularJdk() throws Exception {
        doTest(visitNextEvent);
    }

    /**
     * Patched JVM test for nextTag. Uses the compiled .class file from Maven during the <code>compile</code>
     * phase. Recursion is replaced with a while loop
     */
    @Test
    @JUteTest(jvmOpts = {JVM_ARG_PATCH}, printConsole = true)
    public void testNextEventPatchedJdk() throws Exception {
        checkPatchedJvmSetup();
        doTest(visitNextEvent);
    }

    private void checkPatchedJvmSetup() {
        // pre-check 1
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMxBean.getInputArguments();
        boolean jvmContainsPatch = arguments.stream().anyMatch((jvmArg) -> (JVM_ARG_PATCH).equals(jvmArg));
        Assert.assertTrue("Test is not running with patched JVM argument! " +
                "Please note this JUnit cannot be run from the IDE and must be run through Maven " +
                "in order for the JUnit to run with proper JVM arguments. " +
                "Current JVM arguments: " + arguments, jvmContainsPatch);

        // pre-check 2
        File patchFolder = new File(MAVEN_GENERATED_PATCH_LOCATION + PATCH_FILE_PACKAGE);
        File patchFile = new File(patchFolder, PATCHED_CLASS);
        Assert.assertTrue("Could not find patch file! " +
                "Please note this JUnit cannot be run from the IDE and must " +
                "be run through Maven in order for the JUnit to run with proper JVM arguments. " +
                "Expected patch file location: " + patchFile.getAbsolutePath(), patchFile.exists());
    }

    /**
     * Stress test that attempts to filter everything below the outermost XML tag
     *
     */
    private void doTest(Consumer<XMLEventReader> xmlEventReaderConsumer) throws IOException, XMLStreamException {
        XMLEventReader filteringEventReader = createXmlReader();

        System.out.println("Attempting to read entire XML file, filtering out tag " + TAG_TO_FILTER);
        visitAllEvents(filteringEventReader, xmlEventReaderConsumer);

        System.out.println("Great success, k bye");
    }

    private Consumer<XMLEventReader> visitNextEvent = xmlEventReader -> {
        try {
            xmlEventReader.nextEvent();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Could not read next XML event!", e);
        }
    };

    private XMLEventReader createXmlReader() throws IOException, XMLStreamException {
        File xmlFile = new File(INPUT_XML_FILE);
        System.out.println("Reading XML file for test: " + xmlFile.getAbsolutePath());
        InputStream inputStream = new GZIPInputStream(new FileInputStream(xmlFile));
        XMLInputFactory xif = XMLInputFactory.newInstance();
        XMLEventReader simpleEventReader = xif.createXMLEventReader(inputStream);
        XmlTagEventFilter filter = new XmlTagEventFilter();
        filter.setTagToFilter(TAG_TO_FILTER);
        return xif.createFilteredReader(simpleEventReader, filter);
    }

    private static void visitAllEvents(XMLEventReader filteringEventReader, Consumer<XMLEventReader> xmlEventReaderConsumer) throws XMLStreamException {
        while (true) {
            try {
                // i.e. calls either nextEvent or nextTag
                xmlEventReaderConsumer.accept(filteringEventReader);
            } catch (NoSuchElementException e) {
                break;
            }
        }
    }

    private static void visitAllTags(XMLEventReader filteringEventReader) throws XMLStreamException {
        while (true) {
            try {
                filteringEventReader.nextTag();
            } catch (NoSuchElementException e) {
                break;
            }
        }
    }

    /**
     * XML Event Filter that removes all a specific tag/block by name
     * <p>
     * Note: this class is stateful and must be instantiated for each unmarshaller
     */
    public static class XmlTagEventFilter implements EventFilter {

        private String tagToFilter;

        private AtomicBoolean filterOn = new AtomicBoolean();

        private AtomicLong numEventsFiltered = new AtomicLong();

        @Override
        public boolean accept(XMLEvent event) {
            if (event.isEndElement()) {
                String endTagName = event.asEndElement().getName().getLocalPart();
                if (tagToFilter.equals(endTagName)) {
                    // start accepting the next event
                    System.out.println("Turning off filter! End tag is " + endTagName);
                    System.out.println("Number of events filtered: " + numEventsFiltered.getAndSet(0L));
                    filterOn.set(false);
                    // but filter this one
                    return false;
                }
            }

            if (event.isStartElement()) {
                String startTagName = event.asStartElement().getName().getLocalPart();
                if (tagToFilter.equals(startTagName)) {
                    // exclude this tag and all tags inside
                    System.out.println("Turning on filter! Start tag is: " + startTagName);
                    filterOn.set(true);
                    return false;
                }
            }

            // if filter is off, accept the tag
            if (filterOn.get()) {
                numEventsFiltered.incrementAndGet();
            }
            return !filterOn.get();
        }

        /**
         * @param tagToFilter the name of the XML tag to filter
         */
        public void setTagToFilter(String tagToFilter) {
            this.tagToFilter = tagToFilter;
        }
    }
}
