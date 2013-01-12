package org.jenkinsci.plugins.testinprogress;

import java.io.InputStream;
import java.io.InputStreamReader;

import org.jenkinsci.plugins.testinprogress.events.EventsGenerator;
import org.jenkinsci.plugins.testinprogress.events.ITestEventListener;
import org.jenkinsci.plugins.testinprogress.filters.StackTraceFilter;
import org.jenkinsci.plugins.testinprogress.filters.StackTraceFilterTestRunnerWrapper;
import org.jenkinsci.plugins.testinprogress.messages.ITestRunListener;
import org.jenkinsci.plugins.testinprogress.messages.TestMessagesParser;

/**
 * Thread that receives test messages from an InputStream and add test events to
 * {@link TestEvents}. We use the same message format that eclipse uses.
 * 
 * @author Cedric Chabanois (cchabanois at gmail.com)
 * 
 */
public class TestEventsReceiverThread extends Thread {
	private final InputStream in;
	private final ITestEventListener[] listeners;
	private final StackTraceFilter stackTraceFilter;

	public TestEventsReceiverThread(String threadName, InputStream in,
			StackTraceFilter stackTraceFilter, ITestEventListener[] listeners) {
		super(threadName);
		this.in = in;
		this.listeners = listeners;
		this.stackTraceFilter = stackTraceFilter;
	}

	public void run() {
		EventsGenerator eventsGenerator = new EventsGenerator(listeners);
		StackTraceFilterTestRunnerWrapper wrapper = new StackTraceFilterTestRunnerWrapper(
				eventsGenerator, stackTraceFilter);
		TestMessagesParser parser = new TestMessagesParser(
				new ITestRunListener[] { wrapper });
		parser.processTestMessages(new InputStreamReader(in));
	}
}
