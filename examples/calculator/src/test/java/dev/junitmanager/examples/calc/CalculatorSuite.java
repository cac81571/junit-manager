package dev.junitmanager.examples.calc;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses(CalculatorTest.class)
class CalculatorSuite {
}
