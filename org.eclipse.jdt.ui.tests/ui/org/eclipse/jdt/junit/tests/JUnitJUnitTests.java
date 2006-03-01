/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.junit.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

public class JUnitJUnitTests {

	public static Test suite() {
		TestSuite suite = new TestSuite("Test for org.eclipse.jdt.junit.tests");
		//$JUnit-BEGIN$
		
		// TODO disabled unreliable tests driving the event loop:
//		suite.addTestSuite(WrappingSystemTest.class);
//		suite.addTestSuite(WrappingUnitTest.class);
		
		suite.addTestSuite(TestPriorization.class);
		suite.addTestSuite(TestTestSearchEngine.class);
		
		suite.addTestSuite(TestRunListenerTest.class);
		//$JUnit-END$
		return suite;
	}

}
