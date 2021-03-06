package org.jenkinsci.testinprogress.ant;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import junit.framework.AssertionFailedError;
import junit.framework.ComparisonFailure;
import junit.framework.JUnit4TestAdapter;
import junit.framework.JUnit4TestCaseFacade;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.jenkinsci.testinprogress.messagesender.IMessageSenderFactory;
import org.jenkinsci.testinprogress.messagesender.MessageSender;
import org.jenkinsci.testinprogress.messagesender.SocketMessageSenderFactory;
import org.junit.runner.Description;

/**
 * Formatter to be used with junit ant task. Using
 * <code>JUnitProgressResultFormatter</code> as a formatter allows you to send
 * test unit messages to the jenkins plugin that will display progress as the
 * build is running.
 * 
 * Formatter methods are not called for ignored tests.
 * 
 * @author Cedric Chabanois (cchabanois at gmail.com)
 * 
 */
public class JUnitProgressResultFormatter implements JUnitResultFormatter {
	private MessageSender messageSender;
	private IMessageSenderFactory messageSenderFactory;
	private Map<String, String> testIds = new HashMap<String, String>();
	private static AtomicLong atomicLong = new AtomicLong(0);
	private long startTime;
	private JUnitTest suite;
	private ClassLoader classLoader = null;

	public JUnitProgressResultFormatter() {
		this.messageSenderFactory = new SocketMessageSenderFactory();
	}

	public JUnitProgressResultFormatter(
			IMessageSenderFactory messageSenderFactory) {
		this.messageSenderFactory = messageSenderFactory;
	}

	public void startTestSuite(JUnitTest suite) throws BuildException {
		this.suite = suite;
		if (classLoader == null) {
			return;
		}
		try {
			messageSender = messageSenderFactory.getMessageSender();
			messageSender.init();
			Test test = TestSuiteHelper.getSuite(suite, null);
			messageSender.testRunStarted(test.countTestCases());
			Description description = getDescription(test);
			if (description != null) {
				sendTestTree(description);
			} else if (test instanceof TestSuite) {
				sendTestTree((TestSuite) test);
			} else {
				throw new BuildException("Test suite not supported");
			}
			startTime = System.currentTimeMillis();
		} catch (IOException e) {
			throw new BuildException(e);
		}
	}

	private void sendTestTree(Description description) {
		String id = getTestId(description.getDisplayName());
		messageSender.testTree(id, description.getDisplayName(), description
				.isSuite(), description.isSuite() ? description.getChildren()
				.size() : 1);
		for (Description childDescription : description.getChildren()) {
			sendTestTree(childDescription);
		}
	}

	private void sendTestTree(TestSuite testSuite) {
		String id = getTestId(testSuite.toString());
		messageSender.testTree(id, testSuite.toString(), true,
				testSuite.testCount());
		for (Enumeration<Test> enumeration = testSuite.tests(); enumeration
				.hasMoreElements();) {
			Test childTest = enumeration.nextElement();
			if (childTest instanceof TestSuite) {
				sendTestTree((TestSuite) childTest);
			} else {
				if (childTest.countTestCases() != 1) {
					throw new BuildException("Test not supported :"
							+ childTest.toString());
				}
				String childId = getTestId(childTest.toString());
				messageSender.testTree(childId, childTest.toString(), false, 1);
			}
		}
	}

	private synchronized String getTestId(String testDisplayName) {
		String test = testIds.get(testDisplayName);
		if (test == null) {
			test = Long.toString(atomicLong.incrementAndGet());
			testIds.put(testDisplayName, test);
		}
		return test;
	}

	public void endTestSuite(JUnitTest suite) throws BuildException {
		if (messageSender == null) {
			// no tests
			return;
		}
		try {
			long stopTime = System.currentTimeMillis();
			messageSender.testRunEnded(stopTime - startTime);
			messageSender.shutdown();
		} catch (IOException e) {
			throw new BuildException(e);
		}
	}

	public void startTest(Test test) {
		if (classLoader == null) {
			classLoader = test.getClass().getClassLoader();
			startTestSuite(suite);
		}
		String id = getTestId(test.toString());
		messageSender.testStarted(id, test.toString(), false);
	}

	public void addFailure(Test test, AssertionFailedError t) {
		String id = getTestId(test.toString());
		String expected = null;
		String actual = null;
		if (t instanceof ComparisonFailure) {
			ComparisonFailure comparisonFailure = (ComparisonFailure) t;
			expected = comparisonFailure.getExpected();
			actual = comparisonFailure.getActual();
		}
		messageSender.testFailed(id, test.toString(), expected, actual,
				getTrace(t));
	}

	private String getTrace(Throwable e) {
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}

	public void addError(Test test, Throwable t) {
		String id = getTestId(t.toString());
		messageSender.testError(id, test.toString(), getTrace(t));
	}

	public void endTest(Test test) {
		String id = getTestId(test.toString());
		messageSender.testEnded(id, test.toString(), false);
	}

	private Description getDescription(Test test) {
		if (test instanceof JUnit4TestAdapter) {
			JUnit4TestAdapter adapter = (JUnit4TestAdapter) test;
			return adapter.getDescription();
		}
		if (test instanceof JUnit4TestCaseFacade) {
			JUnit4TestCaseFacade facade = (JUnit4TestCaseFacade) test;
			return facade.getDescription();
		}
		return null;
	}

	public void setOutput(OutputStream out) {
	}

	public void setSystemOutput(String out) {
	}

	public void setSystemError(String err) {
	}

}
